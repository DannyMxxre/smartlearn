# ИИ-наставник

Короткий разговор оценивает, как сотрудник ИТ пользуется ИИ-инструментами, и выдаёт личный план на месяц. Демо для двух ролей: аналитик и разработчик.

Страница: `http://localhost:8080` после запуска из каталога `backend`. Подпись в интерфейсе: S8 Capital • Хакатон Demo.

## Из чего состоит

1. Человек называет роль. Цель на месяц и текущий опыт спрашиваются, но на оценку не влияют.
2. Диагностика идёт по пяти компетенциям роли и двум практическим кейсам. GigaChat ставит баллы 0–3. Уровень считает код.
3. Отчёт показывает уровень, сильные стороны, пробелы, план на месяц и курсы для слабых тем.
4. Тренажёр даёт одно упражнение по пробелу. Тема закрывается, когда названы все пункты задания.

Без ключа GigaChat тот же путь проходит на локальной рубрике. В отчёте это подписано отдельно.

## Документы

- [Роли](docs/Roles.md) — как узнаётся роль и какие пять тем есть у аналитика и разработчика.
- [Диагностика](docs/diagnostics.md) — границы диалога, баллы, уровень и план.
- [Тренажёр](docs/train.md) — задание, разбор попытки и закрытие пробела.
- [Курсы](docs/course_logic.md) — фиксированный каталог и правило, когда ссылка попадает в план.
- [Архитектура](docs/architecture.md) — слои, API, креды и запуск.
- [Исходная постановка](docs/CONTEXT.md) — цель хакатона. Рабочее поведение описано в документах выше: интерфейс — одна страница, модель — GigaChat, курсы не ищутся в сети.

## Код

| Поведение | Где смотреть |
| --- | --- |
| Сценарий и отчёт в чате | `backend/src/main/java/ru/smartlearn/mentor/web/ChatService.java` |
| Диалог и бонусные кейсы | `backend/src/main/java/ru/smartlearn/mentor/llm/DialogueCoach.java` |
| Карта компетенций | `backend/src/main/java/ru/smartlearn/mentor/score/CompetencyMap.java` |
| Уровень и состав отчёта | `backend/src/main/java/ru/smartlearn/mentor/score/AssessmentComposer.java` |
| План на месяц | `backend/src/main/java/ru/smartlearn/mentor/score/PlanTemplates.java` |
| Курсы | `backend/src/main/java/ru/smartlearn/mentor/score/CourseCatalog.java` |
| Тренажёр | `backend/src/main/java/ru/smartlearn/mentor/llm/PracticeCoach.java` |
| Страница | `backend/src/main/resources/static/index.html` |
