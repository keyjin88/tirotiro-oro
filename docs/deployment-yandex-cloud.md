# Развертывание MVP в Yandex Cloud

## 1. Назначение документа

Документ описывает рекомендуемый способ публикации приложения **tirotiro-oro** (Spring Boot 21, Thymeleaf/HTMX, PostgreSQL, Liquibase) в Yandex Cloud для MVP-масштаба:

- 5–30 пользователей всего;
- до 3 одновременных активных сессий;
- аудитория в России, интерфейс на русском языке.

Цель — минимальная стоимость и простота сопровождения без избыточной инфраструктуры.

## 2. Сравнение вариантов развертывания

| Вариант | Плюсы для MVP | Минусы для MVP | Вердикт |
| --- | --- | --- | --- |
| **Compute Cloud VM + Container Registry + Managed PostgreSQL** | Привычный Docker-образ, постоянно работающий JVM, простой SSH/логи, приватное подключение к БД в одной VPC | Две платные сущности (ВМ и БД), ручное обновление образа | **Рекомендуется** |
| **Serverless Containers + Managed PostgreSQL** | Нет управления ОС | Spring Boot/JVM требует `min_instances ≥ 1` (постоянная оплата), холодный старт без подготовленных экземпляров, лимиты памяти/таймаута | Не оптимален |
| **Managed Kubernetes** | Масштабирование, HA | Дорого и сложно для 3 concurrent users | Пропустить |
| **Одна VM + docker-compose (app + postgres)** | Самый дешевый старт | Нет managed-бэкапов, вы сами отвечаете за PostgreSQL | Только для пилота/демо |
| **Cloud Functions / API Gateway** | Дешево для event-driven | Не подходит для серверного рендеринга Thymeleaf | Не применимо |

### Рекомендуемая архитектура

```text
Пользователи (браузер)
        │
        ▼
┌─────────────────────────┐
│  Compute Cloud VM       │  Container Optimized Image
│  Docker: tirotiro-oro   │  2 vCPU, 4 ГБ RAM, порт 8080
│  (публичный IP, опц.)   │
└───────────┬─────────────┘
            │ приватная сеть VPC (порт 6432)
            ▼
┌─────────────────────────┐
│  Managed PostgreSQL     │  1 хост, PostgreSQL 16
│  (без публичного доступа)│  автоматические бэкапы
└─────────────────────────┘

Container Registry ──► хранение Docker-образа
```

**Почему так:**

1. Приложение — долгоживущий JVM-процесс с серверным рендерингом; ему нужна всегда включенная ВМ, а не event-driven serverless.
2. Managed PostgreSQL даёт автоматические бэкапы и обновления СУБД без администрирования PostgreSQL на той же машине.
3. Container Registry — недорогое и стандартное место для образа; ВМ подтягивает его через сервисный аккаунт.
4. Для MVP не нужны Kubernetes, multi-AZ и балансировщик — достаточно одной ВМ и одного хоста БД.

## 3. Ориентировочная стоимость MVP

Точные цифры зависят от тарифов на момент развертывания. Перед запуском сверьте конфигурацию в [калькуляторе Yandex Cloud](https://yandex.cloud/ru/prices).

Ориентир для региона **Россия** (720 ч/мес, июнь 2026):

| Компонент | Рекомендуемая конфигурация | Оценка, ₽/мес |
| --- | --- | --- |
| Managed PostgreSQL | 1 хост `s3-c2-m8` (2 vCPU, 8 ГБ RAM), диск `network-ssd` 10–15 ГБ | ~5 600–5 800 + ~150–200 за диск |
| Compute Cloud VM | 2 × 100% vCPU, 4 ГБ RAM (Intel Ice Lake), boot disk 20 ГБ | ~2 700–3 200 |
| Публичный IP | 1 адрес | ~150–200 |
| Container Registry | образ ~300–500 МБ | ~10–50 |
| Исходящий трафик | до 100 ГБ бесплатно | 0 для MVP |

**Итого:** порядка **8 500–10 000 ₽/мес** при single-host PostgreSQL и одной ВМ.

Как сэкономить:

- PostgreSQL: **1 хост** вместо двух (консоль по умолчанию предлагает 2 — уменьшите до 1).
- PostgreSQL: диск **10 ГБ** `network-ssd` (минимум для MVP более чем достаточен).
- VM: **2 vCPU / 4 ГБ RAM** — минимум для Spring Boot + JVM; 2 ГБ RAM не рекомендуется.
- Не включать Application Load Balancer и CDN до появления реальной необходимости.

> **Примечание.** Кластер PostgreSQL из одного хоста не имеет SLA на высокую доступность и полностью недоступен на время техобслуживания. Для MVP с 5–30 пользователями это приемлемый компромисс.

## 4. Предварительные требования

### Аккаунт и облако

1. [Зарегистрируйте аккаунт](https://yandex.cloud/ru/docs/getting-started/) в Yandex Cloud (юрлицо или физлицо, регион **Россия**).
2. Создайте **каталог** (folder), например `tirotiro-prod`.
3. Привяжите **платежный аккаунт**; для первого развертывания можно использовать стартовый грант.

### Инструменты (CLI опционален)

| Инструмент | Обязательность | Назначение |
| --- | --- | --- |
| [Консоль управления](https://console.cloud.yandex.ru/) | Да | Создание VPC, БД, ВМ, реестра |
| [Yandex Cloud CLI (`yc`)](https://yandex.cloud/ru/docs/cli/quickstart) | Рекомендуется | Автоматизация, push образа, скрипты |
| Docker | Да (локально или CI) | Сборка и push образа |
| SSH-ключ | Да | Доступ к ВМ для диагностики |

### Локальная проверка перед деплоем

```sh
docker compose up --build
# приложение: http://localhost:8080
```

Убедитесь, что образ собирается из корня репозитория (`Dockerfile`, `pom.xml`).

## 5. Пошаговое развертывание

### Шаг 1. Сеть (VPC)

1. **VPC** → создайте сеть `tirotiro-net`.
2. Создайте подсеть, например `tirotiro-subnet-a` в зоне `ru-central1-a`, CIDR `10.1.0.0/24`.
3. При необходимости создайте **NAT-шлюз** — для MVP с публичным IP на ВМ не обязателен, если ВМ имеет внешний адрес.

Для MVP достаточно **одной зоны доступности** (`ru-central1-a`).

### Шаг 2. Managed PostgreSQL

1. **Managed Service for PostgreSQL** → **Создать кластер**.
2. Параметры:

| Параметр | Значение для MVP |
| --- | --- |
| Версия PostgreSQL | **16** (как в `docker-compose.yml`) |
| Окружение | `PRODUCTION` |
| Класс хоста | `s3-c2-m8` (2 vCPU, 8 ГБ RAM) или `s2.micro` |
| Количество хостов | **1** |
| Диск | `network-ssd`, **10–15 ГБ** |
| Публичный доступ | **Выключен** (ВМ и БД в одной VPC) |
| База данных | `equipment` |
| Пользователь | `equipment_app` |
| Пароль | сгенерировать надёжный (≥ 16 символов) |

3. Запишите **FQDN мастера** (формат `c-<cluster_id>.rw.mdb.yandexcloud.net`) — он понадобится для JDBC URL.
4. В разделе **Резервное копирование** оставьте автоматические бэкапы включёнными; для MVP достаточно стандартного окна хранения (7 дней).

Подключение из ВМ в той же VPC — **без SSL** (порт **6432**, не 5432):

```text
jdbc:postgresql://c-<cluster_id>.rw.mdb.yandexcloud.net:6432/equipment
```

Если когда-либо включите публичный доступ к хостам БД, потребуется SSL (`sslmode=verify-full`) и CA-сертификат Yandex Cloud — для MVP это не нужно.

### Шаг 3. Container Registry

1. **Container Registry** → **Создать реестр**, имя `tirotiro-registry`.
2. Запомните ID реестра (`crp...`); полный путь образа: `cr.yandex/<registry_id>/tirotiro-oro:<tag>`.
3. Создайте **сервисный аккаунт** `tirotiro-cr-puller` с ролью `container-registry.images.puller` на каталог.
4. На локальной машине (или в CI):

```sh
yc init   # если ещё не настроен CLI
yc container registry configure-docker

export REGISTRY_ID=<registry_id>
export IMAGE_TAG=0.0.1   # или git SHA

docker build -t tirotiro-oro:${IMAGE_TAG} .
docker tag tirotiro-oro:${IMAGE_TAG} cr.yandex/${REGISTRY_ID}/tirotiro-oro:${IMAGE_TAG}
docker push cr.yandex/${REGISTRY_ID}/tirotiro-oro:${IMAGE_TAG}
```

Для push нужна роль `container-registry.images.pusher` у вашего пользователя или CI-сервисного аккаунта.

### Шаг 4. ВМ с Docker-контейнером

Рекомендуемый способ — **Container Optimized Image** (COI): ОС с предустановленным Docker, автозапуск контейнера.

#### Через CLI

Подготовьте файл `deploy/docker-compose.prod.yml` (локально, не обязательно коммитить):

```yaml
version: "3.7"
services:
  app:
    container_name: tirotiro-oro
    image: cr.yandex/<registry_id>/tirotiro-oro:<tag>
    restart: always
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://c-<cluster_id>.rw.mdb.yandexcloud.net:6432/equipment
      SPRING_DATASOURCE_USERNAME: equipment_app
      SPRING_DATASOURCE_PASSWORD: <secret>
      SPRING_LIQUIBASE_ENABLED: "true"
      SPRING_LIQUIBASE_CONTEXTS: ""
      APP_BOOTSTRAP_ADMIN_EMAIL: <admin@your-domain.ru>
      APP_BOOTSTRAP_ADMIN_PASSWORD: <strong-secret>
      APP_BOOTSTRAP_ADMIN_NAME: "Администратор"
      APP_TIME_ZONE: Europe/Moscow
      APP_SECURITY_REMEMBER_ME_ENABLED: "false"
      SERVER_PORT: "8080"
      JAVA_TOOL_OPTIONS: "-Xms256m -Xmx512m"
```

Создайте ВМ:

```sh
yc compute instance create-with-container \
  --name tirotiro-app \
  --zone ru-central1-a \
  --cores 2 \
  --memory 4 \
  --core-fraction 100 \
  --create-boot-disk size=20,type=network-ssd \
  --network-interface subnet-name=tirotiro-subnet-a,nat-ip-version=ipv4 \
  --service-account-name tirotiro-cr-puller \
  --ssh-key ~/.ssh/id_ed25519.pub \
  --docker-compose-file deploy/docker-compose.prod.yml
```

#### Через консоль

1. **Compute Cloud** → **Создать ВМ**.
2. Образ: **Container Optimized Image**.
3. 2 vCPU, 4 ГБ RAM, 100% vCPU.
4. Подключите сервисный аккаунт `tirotiro-cr-puller`.
5. Укажите Docker-образ и переменные окружения из таблицы ниже.

### Шаг 5. Группы безопасности

| Правило | Направление | Порт | Источник | Назначение |
| --- | --- | --- | --- | --- |
| HTTP приложения | Ingress | 8080 | `0.0.0.0/0` (или IP офиса) | VM |
| SSH администрирование | Ingress | 22 | ваш статический IP | VM |
| PostgreSQL | Ingress | 6432 | подсеть VPC / security group VM | PostgreSQL |

Не открывайте порт 6432 в интернет.

### Шаг 6. Первый запуск и проверка

1. Дождитесь статуса ВМ `RUNNING` и запуска контейнера.
2. Откройте `http://<public_ip>:8080`.
3. Войдите под учётной записью из `APP_BOOTSTRAP_ADMIN_*` (создаётся автоматически, если в БД ещё нет пользователя с ролью ADMIN).
4. Сразу смените пароль администратора через интерфейс или создайте нового admin и удалите bootstrap-учётку.

Проверка логов на ВМ:

```sh
ssh yc-user@<public_ip>
sudo docker logs -f tirotiro-oro
```

При успешном старте Liquibase применит миграции из `db/changelog/db.changelog-master.yaml`.

## 6. Переменные окружения production

Соответствие `src/main/resources/application.yml` и `docker-compose.yml`:

| Переменная | Production | Локально (`docker-compose.yml`) |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://c-....rw.mdb.yandexcloud.net:6432/equipment` | `jdbc:postgresql://postgres:5432/equipment` |
| `SPRING_DATASOURCE_USERNAME` | `equipment_app` | `equipment_app` |
| `SPRING_DATASOURCE_PASSWORD` | секрет из Lockbox / env | `change-me` |
| `SPRING_LIQUIBASE_ENABLED` | `true` | `true` (по умолчанию) |
| `SPRING_LIQUIBASE_CONTEXTS` | **пусто или не задавать** | `local` |
| `APP_BOOTSTRAP_ADMIN_EMAIL` | реальный email администратора | `admin@example.local` |
| `APP_BOOTSTRAP_ADMIN_PASSWORD` | надёжный пароль | `change-me-admin` |
| `APP_BOOTSTRAP_ADMIN_NAME` | отображаемое имя | `Local Admin` |
| `APP_TIME_ZONE` | `Europe/Moscow` | не задан (default Moscow) |
| `APP_SECURITY_REMEMBER_ME_ENABLED` | `false` | не задан |
| `SERVER_PORT` | `8080` | `8080` |

### Критически важно для production

1. **`SPRING_LIQUIBASE_CONTEXTS` не должен содержать `local`.**  
   Контекст `local` включает changeset `006-local-demo-data.yaml` с демо-пользователями (`producer@example.local` и др.). В production этот контекст **не задавайте**.

2. **`APP_BOOTSTRAP_ADMIN_EMAIL` и `APP_BOOTSTRAP_ADMIN_PASSWORD` обязательны** при первом запуске на пустой БД — иначе приложение завершится с ошибкой (`BootstrapAdminInitializer`).

3. **Пароли** не храните в git. Для MVP допустимы переменные окружения на ВМ; лучше — [Yandex Lockbox](https://yandex.cloud/ru/docs/lockbox/) с монтированием секретов в COI.

4. **`SPRING_PROFILES_ACTIVE=prod`** — опционально; отдельный профиль `application-prod.yml` в репозитории пока не требуется, достаточно env vars.

## 7. Обновление приложения

```sh
# 1. Собрать и запушить новый тег
docker build -t tirotiro-oro:${NEW_TAG} .
docker tag tirotiro-oro:${NEW_TAG} cr.yandex/${REGISTRY_ID}/tirotiro-oro:${NEW_TAG}
docker push cr.yandex/${REGISTRY_ID}/tirotiro-oro:${NEW_TAG}

# 2. На ВМ — обновить образ и перезапустить
ssh yc-user@<public_ip>
sudo docker pull cr.yandex/${REGISTRY_ID}/tirotiro-oro:${NEW_TAG}
# обновите tag в docker-compose и:
sudo docker compose -f /path/to/docker-compose.prod.yml up -d
```

Перед обновлением с миграциями Liquibase убедитесь, что создан свежий backup PostgreSQL (см. раздел 9).

## 8. HTTPS и домен (опционально для MVP)

Для внутреннего MVP допустим доступ по `http://<ip>:8080`. Для публичного доступа с доменом:

### Минимальный вариант (без балансировщика)

- Настройте A-запись домена на публичный IP ВМ.
- Установите на ВМ **Caddy** или **nginx** как reverse proxy с Let's Encrypt (certbot).
- Проксируйте `443 → localhost:8080`.

Плюс: дешевле. Минус: сертификат и proxy на той же ВМ, ручное сопровождение.

### Вариант Yandex Cloud (рекомендуется при росте)

1. [Yandex Cloud DNS](https://yandex.cloud/ru/docs/dns/) — зона для домена.
2. [Certificate Manager](https://yandex.cloud/ru/docs/certificate-manager/) — managed-сертификат Let's Encrypt (challenge `DNS_CNAME`).
3. [Application Load Balancer](https://yandex.cloud/ru/docs/application-load-balancer/) — L7 listener на 443, backend group → ВМ:8080.

ALB добавляет ~1 500–2 500 ₽/мес — для MVP можно отложить.

## 9. Резервное копирование и восстановление

### PostgreSQL (Managed)

- Автоматические бэкапы включены по умолчанию; хранятся в пределах объёма диска кластера бесплатно.
- Перед крупными миграциями: **Managed PostgreSQL** → кластер → **Создать резервную копию** вручную.
- Восстановление: создать новый кластер из backup или point-in-time recovery (если включено).

### Приложение

- Код и образ — в git + Container Registry (храните теги релизов).
- Состояние приложения stateless; единственный источник данных — PostgreSQL.

### RPO/RTO для MVP

| Показатель | Разумная цель MVP |
| --- | --- |
| RPO (потеря данных) | ≤ 24 ч (ежедневный auto-backup) |
| RTO (время восстановления) | 1–4 ч (ручное развертывание ВМ из образа + restore БД) |

## 10. Базовый чеклист безопасности

- [ ] PostgreSQL **без публичного доступа**; подключение только из VPC.
- [ ] Уникальные пароли для `equipment_app` и bootstrap-admin (≥ 16 символов).
- [ ] **`SPRING_LIQUIBASE_CONTEXTS`** не содержит `local`.
- [ ] SSH только с доверенных IP; ключи, не пароли.
- [ ] Security groups: минимально необходимые порты.
- [ ] Регулярные обновления образа (базовый JRE, зависимости Maven).
- [ ] После первого входа — смена bootstrap-пароля или удаление bootstrap-учётки.
- [ ] `APP_SECURITY_REMEMBER_ME_ENABLED=false` до настройки HTTPS (remember-me cookie без Secure-флага нежелателен).
- [ ] Мониторинг биллинга и алертов в Yandex Cloud Billing.

## 11. Что сознательно пропустить на MVP

| Компонент | Почему не нужен сейчас |
| --- | --- |
| Kubernetes / MKS | 1 инстанс приложения, 3 concurrent users |
| Multi-AZ PostgreSQL (2+ хоста) | +100% стоимости БД; SLA не критичен |
| Instance Groups / autoscaling | Нагрузка предсказуема и низкая |
| CDN | Нет статического SPA; HTMX-фрагменты малы |
| Отдельный Secret Manager / CI/CD | Можно добавить позже; для MVP хватит env + ручного deploy |
| WAF (Smart Web Security) | Избыточно для закрытой команды |
| Prometheus/Grafana | Достаточно логов Docker + healthcheck; Actuator можно включить позже |
| Read replicas PostgreSQL | Нет нагрузки на чтение |

## 12. Отличия локального и production окружения

| Аспект | Локально | Production (Yandex Cloud) |
| --- | --- | --- |
| PostgreSQL | Контейнер `postgres:16-alpine` в compose | Managed PostgreSQL 16, порт 6432 |
| Сеть | `postgres` как hostname | FQDN `c-....mdb.yandexcloud.net` |
| Демо-данные | `SPRING_LIQUIBASE_CONTEXTS=local` | контекст **не задан** |
| Bootstrap admin | тестовые credentials | реальные credentials, одноразово |
| HTTPS | не используется | опционально (см. раздел 8) |
| Пароли | `change-me` | секреты через Lockbox / env |
| Thymeleaf cache | может отличаться при dev-профиле | `spring.thymeleaf.cache=true` (default) |
| JVM heap | default | рекомендуется `-Xms256m -Xmx512m` |
| Бэкапы | volume Docker, без автоматики | managed auto-backup |

## 13. Устранение типичных проблем

| Симптом | Возможная причина | Решение |
| --- | --- | --- |
| `Connection refused` к БД | неверный FQDN или security group | проверьте FQDN, port 6432, SG между VM и PG |
| `Пользователь ADMIN не найден...` | не заданы bootstrap env | задайте `APP_BOOTSTRAP_ADMIN_*` |
| Демо-пользователи в production | включён context `local` | уберите `SPRING_LIQUIBASE_CONTEXTS` |
| OOM / контейнер перезапускается | мало RAM | увеличьте VM до 4 ГБ, ограничьте `-Xmx512m` |
| Образ не pull-ится | нет роли у SA | `container-registry.images.puller` на SA ВМ |
| Liquibase validation error | БД не пустая / конфликт схемы | restore backup или чистая БД |

## 14. Полезные ссылки

- [Managed PostgreSQL — быстрый старт](https://yandex.cloud/ru/docs/managed-postgresql/quickstart)
- [Container Registry — push образа](https://yandex.cloud/ru/docs/container-registry/operations/docker-image/docker-image-push)
- [COI — ВМ с Docker Compose](https://yandex.cloud/ru/docs/tutorials/container-infrastructure/docker-compose)
- [Тарифы Managed PostgreSQL](https://yandex.cloud/ru/docs/managed-postgresql/pricing)
- [Тарифы Compute Cloud](https://yandex.cloud/ru/docs/compute/pricing)
- [Техническая спецификация проекта](./technical-spec-equipment-warehouse.md) — раздел 16 «Docker и конфигурация»
