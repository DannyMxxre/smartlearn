# Архитектура

Один процесс Spring Boot отдаёт и страницу, и API. Отдельного фронтенд-приложения нет. Сессии живут в памяти и пропадают при перезапуске. Учебные тексты выдуманы, внешних данных сотрудника нет.

## Слои

```text
index.html
    POST /api/chat
    GET  /api/status
        ChatController / StatusController
            ChatService
                DialogueCoach          диагностика
                PracticeCoach          тренажёр
                AssessmentComposer     отчёт
                    CompetencyRollup
                    LevelScale
                    PlanTemplates
                    CourseCatalog
                GigaChatClient         если заданы креды
                HeuristicAssessor      если кредов нет или JSON сломан
            SessionStore               ConcurrentHashMap
```

Страница — `backend/src/main/resources/static/index.html`. Это один файл на Tailwind: лента чата, карточка уровня, пробелы с кнопкой «Отработать», план и курсы. Ответы наставника рисуются как Markdown.

## Кто за что отвечает

| Часть | Класс | Что делает |
| --- | --- | --- |
| Сценарий | `web/ChatService` | Фазы, знакомство, вызов диалога и тренажёра, текст отчёта |
| Фазы | `dialog/Phase` | `intro`, `dialogue`, `bonus`, `practice`, `report` |
| Сессия | `dialog/MentorSession`, `dialog/SessionStore` | Роль, наблюдения, отчёт, текущее задание тренажёра |
| Вопросы | `score/CompetencyMap` | Пять тем и два бонусных кейса на роль |
| Баллы диалога | `llm/DialogueCoach` | 6–10 реплик, уточнения, бонусы, запрос к модели |
| Сводка | `score/CompetencyRollup` | Средний балл темы |
| Уровень | `score/LevelScale` | Доля суммы, не решение модели |
| План | `score/PlanTemplates` | Четыре недели по баллам |
| Курсы | `score/CourseCatalog` | Шесть заранее заданных ссылок |
| Тренажёр | `llm/PracticeCoach` | Кейс, разбор попытки, запасной похожий кейс |
| Модель | `llm/GigaChatClient` | OAuth и `chat/completions` |
| Запас | `score/HeuristicAssessor` | Балл по длине и рабочим признакам в тексте |

`llm/LlmAssessor`, `llm/AssessmentService` и `question/QuestionBank` остались от раннего сценария с фиксированными вопросами. Текущий чат их не вызывает.

## Модель

Креды читает `config/EnvFile` из `backend/.env` до старта Spring. Уже заданные переменные окружения файл не перетирает. Нужны `GIGACHAT_CLIENT_ID`, `GIGACHAT_SCOPE` и `GIGACHAT_AUTHORIZATION_KEY`. Имена и комментарии лежат в `backend/.env.example`. Сам `.env` в git не входит.

Клиент ходит за токеном на `https://ngw.devices.sberbank.ru:9443/api/v2/oauth` и за ответом на `https://api.giga.chat/v1/chat/completions`. Модель по умолчанию — `GigaChat-2`. Для TLS к хранилищу JVM добавлены корневой и промежуточный сертификаты Минцифры из `src/main/resources/certs/`.

`GET /api/status` возвращает только флаг `gigachatConnected`. Ключ в ответ не попадает. Боковая подпись на странице показывает «подключено» или «не подключено».

## Запуск

Нужна Java 25, в этом окружении это Temurin. Из каталога `backend`:

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home
mvn spring-boot:run
```

Страница открывается на `http://localhost:8080`. После правки кода или `.env` процесс нужно перезапустить: `spring-boot:run` изменения сам не подхватывает.
