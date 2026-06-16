# Размещение MVP на хостинге в России: обзор провайдеров

## 1. Назначение документа

Документ помогает выбрать **российский хостинг** для MVP **tirotiro-oro** (Java 21, Spring Boot, Thymeleaf/HTMX, PostgreSQL 16, Liquibase, Docker) при масштабе:

- 5–30 пользователей всего;
- до 3 одновременных сессий;
- оплата в рублях, доступ из РФ без обходных схем.

Фокус — **минимальная стоимость** и **простота** для монолита Docker + PostgreSQL. Пошаговое развёртывание в Yandex Cloud описано отдельно: [deployment-yandex-cloud.md](./deployment-yandex-cloud.md).

> **Актуальность.** Цены и тарифы сверялись по открытым прайс-листам провайдеров в **июне 2026**. Перед запуском пересчитайте конфигурацию в калькуляторе выбранного провайдера — тарифы меняются (например, у Timeweb Cloud повышение с декабря 2025, у Beget — с апреля 2026).

## 2. Что нужно приложению

| Компонент | Минимум для MVP | Комментарий |
| --- | --- | --- |
| JVM (Spring Boot) | 2 vCPU, **4 ГБ RAM** | 2 ГБ RAM для JVM — на грани; см. [deployment-yandex-cloud.md](./deployment-yandex-cloud.md) |
| PostgreSQL 16 | 1–2 vCPU, 2–4 ГБ RAM, 10–20 ГБ диск | Для MVP достаточно одного хоста без HA |
| Сеть | 1 публичный IP, HTTPS (443) | Трафик для 5–30 пользователей — единицы ГБ/мес |
| Docker | docker compose на VPS **или** образ на VM в облаке | В репозитории есть `docker-compose.yml` (app + postgres) |

Два типовых паттерна:

1. **Одна VPS + docker-compose** — app и PostgreSQL на одной машине (самый дешёвый старт).
2. **VPS/VM для app + Managed PostgreSQL** — дороже, но автоматические бэкапы и меньше администрирования БД.

## 3. Обзор провайдеров (доступ из РФ, оплата ₽)

Все перечисленные ниже — **российские** провайдеры с дата-центрами в РФ. Оплата картой (МИР, российские Visa/MC где доступны), СБП, безнал для юрлиц. Иностранные облака (AWS, GCP, Azure, Hetzner, DigitalOcean и т.п.) для MVP из РФ **не рекомендуются**: сложности с оплатой, санкционные риски, задержки доступа. С 2027 г. для части организаций планируются ограничения на хранение ПДн в иностранных облаках ([Ведомости, 08.2025](https://www.vedomosti.ru/technology/articles/2025/08/08/1130176-krupnomu-biznesu-zapretyat-ispolzovat-inostrannie-oblaka)).

| Провайдер | Тип | Managed PostgreSQL | Кратко |
| --- | --- | --- | --- |
| **Yandex Cloud** | Полноценное облако | Да (PG 16) | Эталон для «облачного» MVP; подробности — [deployment-yandex-cloud.md](./deployment-yandex-cloud.md) |
| **VK Cloud** (Mail.ru) | Облако | Да | Pay-as-you-go, VPC, Container Registry; промо до ~12 000 ₽ на тест |
| **Cloud.ru** (бывш. SberCloud) | Облако (Evolution / Advanced) | Да | Корпоративный сегмент, сложнее и дороже для малого MVP |
| **Selectel** | VPS + облако | Да (от ~2 685 ₽/мес) | Tier III, DevOps-ориентирован, Terraform/API |
| **Timeweb Cloud** | VPS + DBaaS | Да (фикс. тарифы от 790 ₽/мес) | Простые пресеты, почасовой биллинг VPS |
| **Reg.ru / Reg.cloud** | VPS + облако | Да (от 784 ₽/мес) | Почасовая оплата VPS, ispmanager в комплекте |
| **Beget** | VPS + DBaaS | Да (PG 14–16) | Маркетплейс Docker, линейный конфигуратор; IP отдельно с 04.2026 |
| **Reg.ru VPS** (классика) | VPS | Нет (самостоятельно) | Низкий порог входа, shared nothing |

## 4. Сравнительная таблица для MVP

Оценка для конфигурации **приложение 2 vCPU / 4 ГБ RAM** + **PostgreSQL** (один хост, 10–20 ГБ). Сценарии:

- **A** — одна VPS, `docker-compose` (app + postgres);
- **B** — VPS/VM для app + Managed PostgreSQL;
- **C** — облако: VM + Managed PG + registry (как в Yandex-гайде).

| Провайдер | Стек (рекомендуемый для MVP) | ~₽/мес (A) | ~₽/мес (B) | ~₽/мес (C) | Сложность | Плюсы для tirotiro-oro | Минусы для tirotiro-oro |
| --- | --- | --- | --- | --- | --- | --- | --- |
| **Yandex Cloud** | VM 2/4 + Managed PG 2/8 + Registry | ~3 500* | ~8 500–10 000 | ~8 500–10 000 | Средняя | VPC, бэкапы PG, COI, детальный гайд в репозитории | Дороже VPS-провайдеров; две платные сущности |
| **Timeweb Cloud** | Cloud MSK 50 + Cloud DB 2/4/40 | **~1 100** | **~2 600–2 800** | — | **Низкая** | Фиксированные тарифы, Docker на VPS, PG 14–18, бесплатный трафик | Меньше «enterprise»-функций, чем у крупных облаков |
| **Beget** | VPS 2/4 (Docker) + DBaaS 2/20 | **~800–1 200** | **~1 800–2 200** | — | **Низкая** | Готовый образ Docker Compose, простая панель | С 09.04.2026 IP +150 ₽/мес отдельно; PG только через публичную сеть или приватку Beget |
| **Reg.ru** | High C2-M4-D60 + Managed PG C2-M4-D80 | ~2 200 | ~4 900 | — | Низкая | Почасовая оплата VPS, PG 17, DDoS в базе | 2 ГБ RAM на Std-тарифах — мало для JVM |
| **Selectel** | VDS 2-4-50 + Managed PG мин. | ~600–800** | ~3 400–3 800 | — | Средняя | Tier III, SLA 99,95%, приватные сети | Мин. Managed PG (~2 685 ₽) завышен для 5–30 users; интерфейс сложнее Timeweb |
| **VK Cloud** | VM STD2-2-4 + Cloud Databases PG | ~2 800–3 200 | ~6 500–7 500 | ~7 000+ | Средняя | Pay-as-you-go, бесплатный трафик 1 Гбит/с, промо-баланс | Поминутная тарификация; нужно следить за 2+ нодами PG по умолчанию |
| **Cloud.ru** | Evolution VM + Managed PG | ~3 500+ | ~6 000–8 000 | ~8 000+ | **Высокая** | Сбер-экосистема, 152-ФЗ-решения | Избыточен и дорог для 3 concurrent users |
| **Reg.ru Std VPS** | Std C2-M2-D40, docker-compose | ~980 | — | — | Низкая | Очень дёшево, IP в цене | **2 ГБ RAM** — риск OOM для JVM + PostgreSQL |

\* Одна VM + docker-compose в Yandex возможна (~2 700 ₽ VM + IP), но без managed-бэкапов; в [deployment-yandex-cloud.md](./deployment-yandex-cloud.md) это отмечено как «только пилот».

\*\* Точная цена VDS 2-4-50 на сайте Selectel не всегда указана в пресете; ориентир — между минимальным VDS (~200 ₽) и VDS 4-8-80 (1 100 ₽).

### Источники цен (июнь 2026)

- [Yandex Cloud — оценка в deployment-yandex-cloud.md](./deployment-yandex-cloud.md)
- [Timeweb Cloud VPS](https://timeweb.cloud/services/vds-vps), [Managed PostgreSQL](https://timeweb.cloud/services/postgresql)
- [Beget Docker marketplace](https://beget.com/ru/cloud/marketplace/docker), [DBaaS PostgreSQL](https://beget.com/ru/cloud/dbaas-postgresql)
- [Reg.ru VPS](https://www.reg.ru/vps/), [Reg.cloud PostgreSQL](https://reg.cloud/services/postgresql)
- [Selectel VPS/VDS](https://selectel.ru/services/cloud/vps-vds/), [Managed PostgreSQL](https://selectel.ru/services/cloud/managed-databases/postgresql/)
- [VK Cloud прайс-лист](https://cloud.vk.com/pricelist/), [калькулятор](https://cloud.vk.com/pricing/)
- [Cloud.ru Evolution Managed PostgreSQL](https://cloud.ru/documents/tariffs/evolution/managed-postgresql)

## 5. Рекомендации (кроме / вместе с Yandex Cloud)

### Основной детальный гайд

**Yandex Cloud** — если нужны managed-бэкапы, VPC и «облачный» путь с Container Registry: следуйте [deployment-yandex-cloud.md](./deployment-yandex-cloud.md). Ориентир **~8 500–10 000 ₽/мес** (VM + Managed PG).

### Топ-3 альтернативы с фокусом на стоимость

#### 1. Timeweb Cloud — лучший баланс цена / простота / Managed PG

- **Сценарий B (рекомендуется):** [Cloud MSK 50](https://timeweb.cloud/services/vds-vps) (2 vCPU, 4 ГБ, 50 ГБ NVMe, ~1 062 ₽) + [Cloud DB 2/4/40](https://timeweb.cloud/services/postgresql) (~1 580 ₽) ≈ **2 600–2 800 ₽/мес** (публичный IP для VPS уже в тарифе; для PG — приватная сеть).
- **Сценарий A (пилот):** только Cloud MSK 50 + `docker-compose` ≈ **~1 100 ₽/мес**.
- **Почему:** фиксированные тарифы без сюрпризов pay-as-you-go, PostgreSQL 16/17/18, автобэкапы DBaaS, минимальный порог для команды без DevOps.

#### 2. Beget — самый дешёвый старт с Docker «из коробки**

- **Сценарий A:** VPS с [маркетплейсом Docker](https://beget.com/ru/cloud/marketplace/docker) (2 vCPU / 4 ГБ — через конфигуратор) + `docker-compose` ≈ **800–1 200 ₽/мес** (+ 150 ₽ IP с 09.04.2026).
- **Сценарий B:** VPS + [DBaaS PostgreSQL](https://beget.com/ru/cloud/dbaas-postgresql) 2 ГБ / 20 ГБ (~990 ₽/мес) ≈ **1 800–2 200 ₽/мес**.
- **Почему:** минимальные деньги, готовый Docker Compose на Ubuntu 24.04, соответствие 152-ФЗ заявлено провайдером.
- **Ограничение:** меньше гибкости private cloud, чем у Yandex/VK; для production с ПДн — заранее проверить договор и DPA.

#### 3. VK Cloud — альтернатива Yandex Cloud «полного облака»

- **Сценарий B/C:** VM 2 vCPU / 4 ГБ (~2 590 ₽) + Managed PostgreSQL 2 vCPU / 4 ГБ (~3 700 ₽ за single-node, без реплик) + IP/диск ≈ **6 500–7 500 ₽/мес**.
- **Почему:** полноценное облако (VPC, security groups, registry), промо **до 12 000 ₽** на тест ([cloud.vk.com](https://cloud.vk.com/)), бесплатный исходящий трафик.
- **Ограничение:** при создании PG-кластера **уменьшите число нод до 1**, иначе стоимость удвоится; биллинг pay-as-you-go требует лимитов расходов.

### Когда выбрать Selectel или Reg.ru

- **Selectel** — если важны Tier III, SLA и интеграция с bare metal / Kubernetes в перспективе; для чистого MVP дороже Timeweb при сопоставимых задачах.
- **Reg.ru** — если уже есть домены/хостинг Reg.ru и нужен один провайдер; для JVM лучше линейка **High** (4 ГБ RAM), не Std.

### Когда не брать Cloud.ru (SberCloud) для MVP

Платформа ориентирована на enterprise (Evolution/Advanced, договоры, 152-ФЗ-комплаенс). Для 5–30 пользователей и одного монолита **Timeweb или Yandex** проще и дешевле.

## 6. VPS-only vs Managed PostgreSQL

| Критерий | Одна VPS + docker-compose | VPS/VM + Managed PostgreSQL |
| --- | --- | --- |
| **Стоимость** | **~800–1 200 ₽/мес** (минимум) | **~2 000–10 000 ₽/мес** |
| **Сложность деплоя** | Низкая: `git pull`, `docker compose up -d` | Средняя: отдельные credentials, firewall, private network |
| **Бэкапы БД** | Ваши (`pg_dump`, cron, снимки VPS) | Автоматические у провайдера |
| **Обновления PG / патчи** | Вручную (образ `postgres:16-alpine`) | Managed minor updates |
| **Отказоустойчивость** | Single point of failure | Single-node PG без SLA HA — downtime на техобслуживание |
| **Когда выбирать** | Пилот, demo, внутренний MVP, жёсткий бюджет | Первые реальные пользователи, ПДн, нужен сон админа |

**Практическая схема для tirotiro-oro:**

1. **Пилот (1–2 недели):** одна VPS, `docker-compose.yml` из репозитория, бэкап volume вручную.
2. **MVP в production:** VPS/VM для app + Managed PostgreSQL (Timeweb / Yandex / VK) — данные переносятся через `pg_dump` / Liquibase на чистой БД.
3. **Не смешивать** managed PG с docker-compose postgres на той же машине — один источник данных.

Минимальные требования к VPS-only: **не менее 4 ГБ RAM** на одной машине (JVM ~1,5–2 ГБ + PostgreSQL ~512 МБ–1 ГБ + ОС). Конфигурации 2 ГБ (Reg.ru Std, Timeweb MSK 40) — только для локальной отладки, не для production.

## 7. Оплата и правовые заметки (РФ)

| Тема | Кратко |
| --- | --- |
| **Физлица / ИП** | Карта (МИР, российские банки), СБП, иногда рекуррентные списания (Cloud.ru, Yandex). Чеки по 54-ФЗ. |
| **Юрлица (ООО и др.)** | Договор/оферта, оплата по счёту в рублях, акт + счёт-фактура (НДС — по тарифу провайдера). Yandex Cloud — [договор](https://yandex.cloud/ru/docs/billing/qa/contract); двусторонний договор ~2 недели. |
| **152-ФЗ / ПДн** | Для учёта сотрудников/контрагентов вероятна обработка ПДн. Уточните у провайдера: локализация в РФ, DPA, уровень защиты. Beget и Yandex заявляют решения под 152-ФЗ; для юрлица — оформление через договор. |
| **Санкции / доступ** | Российские провайдеры доступны из РФ без VPN. Зарубежные CDN/аналитика могут быть недоступны — не критично для MVP. |
| **Резидентство данных** | Дата-центры провайдеров — Москва, СПб, Новосибирск и др.; для MVP достаточно одного региона. |

## 8. Быстрый чеклист выбора

```text
Бюджет < 1 500 ₽/мес     → Beget или Timeweb VPS-only (docker-compose)
Бюджет 2 000–3 000 ₽/мес → Timeweb VPS + Managed PG  ★ sweet spot
Нужен «облачный» стандарт → Yandex Cloud (см. deployment-yandex-cloud.md)
Уже в экосистеме VK/Mail → VK Cloud (single-node PG, лимит расходов)
Enterprise / 152-ФЗ       → Yandex Cloud или Cloud.ru + юридическое сопровождение
```

## 9. Связанные документы

| Документ | Содержание |
| --- | --- |
| [deployment-yandex-cloud.md](./deployment-yandex-cloud.md) | **Основной пошаговый гайд:** VM, Container Registry, Managed PostgreSQL, COI |
| [technical-spec-equipment-warehouse.md](./technical-spec-equipment-warehouse.md) | Требования к приложению, Docker, переменные окружения |
| [deployment-regru.md](./deployment-regru.md) | **Reg.ru VPS:** docker-compose, обновление `update-remote.sh` |

---

*Документ не дублирует инструкции Yandex Cloud — для развёртывания там используйте [deployment-yandex-cloud.md](./deployment-yandex-cloud.md).*
