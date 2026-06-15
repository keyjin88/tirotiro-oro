# Tirotiro Oro Equipment Warehouse

MVP на Spring Boot для учета оборудования, проверки доступности, бронирований и административных складских процессов.

## Локальные команды

Запустить полный набор проверок, включая интеграционные тесты и порог покрытия JaCoCo:

```sh
mvn verify
```

Интеграционный тест PostgreSQL использует Testcontainers и требует доступного Docker daemon из того же окружения, где запускается Maven. Перед запуском `mvn verify` проверьте, что команды `docker info` и `docker run --rm hello-world` проходят в этом же терминале. Для Docker Engine 29 используется Testcontainers `1.21.4`; более ранняя версия может ошибочно считать Docker недоступным и пропускать PostgreSQL IT.

Собрать и запустить приложение с PostgreSQL:

```sh
docker compose up --build
```

Пересобрать образ приложения и перезапустить контейнер для применения последних изменений без удаления данных PostgreSQL:

```sh
docker compose up -d --build --force-recreate app
```

Приложение доступно на `http://localhost:8080`. Compose-стек создает локальную базу PostgreSQL с именем `equipment`, пользователем `equipment_app` и паролем `change-me`.

Compose также включает Liquibase-контекст `local` через `SPRING_LIQUIBASE_CONTEXTS=local`. Он добавляет демонстрационные учетные записи, категории, оборудование и бронирования в текущем месяце. Вне compose этот контекст по умолчанию не включен; задавайте `SPRING_LIQUIBASE_CONTEXTS=local` только для локальной разработки или демо-окружений.

При первом запуске приложение создает администратора только если в базе еще нет пользователя с ролью `ADMIN`. Локальные значения по умолчанию для compose:

```text
APP_BOOTSTRAP_ADMIN_EMAIL=admin@example.local
APP_BOOTSTRAP_ADMIN_PASSWORD=change-me-admin
APP_BOOTSTRAP_ADMIN_NAME=Local Admin
```

Войдите на `http://localhost:8080/login` с этим email и паролем. Измените эти значения перед использованием общего или постоянного окружения. Если администратора нет, а `APP_BOOTSTRAP_ADMIN_EMAIL` или `APP_BOOTSTRAP_ADMIN_PASSWORD` не задана, запуск завершится с понятной ошибкой bootstrap; после появления администратора bootstrap-настройки игнорируются.

Если стек уже запускался до добавления bootstrap-администратора, пересоберите образ командой `docker compose up --build`. Если в сохраненной локальной базе уже есть старый пользователь с ролью `ADMIN`, но его пароль неизвестен, сбросьте локальные данные командой `docker compose down -v`, затем снова выполните `docker compose up --build`. Команда `down -v` удаляет локальный volume PostgreSQL и все тестовые данные.

Остановить стек:

```sh
docker compose down
```
