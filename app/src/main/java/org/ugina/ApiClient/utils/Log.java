package org.ugina.ApiClient.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Удобная обёртка над SLF4J для логирования в рамках фреймворка.
 *
 * SLF4J (Simple Logging Facade for Java) — это НЕ логгер, а фасад (интерфейс).
 * Сам по себе он ничего не пишет. За ним стоит реализация — в нашем случае Logback.
 * Цепочка: наш код → SLF4J (фасад) → Logback (реализация) → консоль/файл.
 *
 * Зачем обёртка, если SLF4J и так удобный?
 *
 * 1. ЕДИНЫЙ ФОРМАТ — все логи фреймворка выглядят одинаково:
 *    [2026-04-13 14:30:05] [INFO] [main] → POST /users | status=201 | 45ms
 *    Без обёртки каждый разработчик форматирует по-своему.
 *
 * 2. ПОТОКОБЕЗОПАСНОСТЬ — Logger из SLF4J потокобезопасен сам по себе,
 *    но мы добавляем имя потока в каждую строку. В многопоточных тестах TestNG
 *    это позволяет понять, какой поток что делает.
 *
 * 3. РАСШИРЯЕМОСТЬ — позже можно добавить запись в файл, отправку в Allure,
 *    фильтрацию по уровню — и всё в одном месте, а не по всему проекту.
 *
 * Как использовать:
 *
 *   // Создаём логгер для конкретного класса
 *   private static final Log log = Log.forClass(MyTest.class);
 *
 *   // Логируем
 *   log.info("Запрос отправлен");
 *   log.info("GET /users | status={} | duration={}ms", 200, 45);
 *   log.error("Что-то пошло не так", exception);
 */
public class Log {

    // Формат времени: 2026-04-13 14:30:05.123
    // DateTimeFormatter — потокобезопасен, создаём один раз.
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    // SLF4J Logger — делегат, который реально пишет логи.
    private final Logger logger;

    // ──── Создание ────

    /**
     * Приватный конструктор — создаём только через фабричные методы.
     */
    private Log(Logger logger) {
        this.logger = logger;
    }

    /**
     * Создаёт логгер для указанного класса.
     * Имя класса будет отображаться в логах Logback (если настроено в pattern).
     *
     * Пример: Log log = Log.forClass(ApiClient.class);
     */
    public static Log forClass(Class<?> clazz) {
        return new Log(LoggerFactory.getLogger(clazz));
    }

    // ──── Уровни логирования ────
    //
    // SLF4J определяет 5 уровней (от самого тихого к самому громкому):
    //   TRACE → DEBUG → INFO → WARN → ERROR
    //
    // В logback.xml мы ставим уровень (например, INFO), и всё что ниже — игнорируется.
    // Если level=INFO, то TRACE и DEBUG не будут печататься.

    /**
     * INFO — основной рабочий уровень.
     * Что логировать: отправка запроса, получение ответа, начало/конец теста.
     *
     * @param message текст сообщения. Поддерживает плейсхолдеры {} из SLF4J.
     * @param args    значения для подстановки в {}
     */
    public void info(String message, Object... args) {
        if (logger.isInfoEnabled()) {
            logger.info(format("INFO", message, args));
        }
    }

    /**
     * WARN — предупреждение. Не ошибка, но что-то подозрительное.
     * Что логировать: retry запроса, fallback на локальный драйвер, таймаут близок к лимиту.
     */
    public void warn(String message, Object... args) {
        if (logger.isWarnEnabled()) {
            logger.warn(format("WARN", message, args));
        }
    }

    /**
     * ERROR — ошибка. Что-то сломалось.
     * Что логировать: запрос упал, тест провалился, драйвер не создался.
     */
    public void error(String message, Object... args) {
        if (logger.isErrorEnabled()) {
            logger.error(format("ERROR", message, args));
        }
    }

    /**
     * ERROR с исключением — логирует сообщение + стек-трейс.
     * Throwable передаётся последним аргументом — SLF4J выведет его стек-трейс отдельно.
     */
    public void error(String message, Throwable throwable) {
        if (logger.isErrorEnabled()) {
            logger.error(format("ERROR", message), throwable);
        }
    }

    /**
     * DEBUG — детальная информация для отладки.
     * Что логировать: тело запроса/ответа, заголовки, внутреннее состояние.
     * В проде обычно выключен (level=INFO в logback.xml).
     */
    public void debug(String message, Object... args) {
        if (logger.isDebugEnabled()) {
            logger.debug(format("DEBUG", message, args));
        }
    }

    // ──── Форматирование ────

    /**
     * Собирает строку лога в нужном формате:
     * [2026-04-13 14:30:05.123] [INFO] [thread-name] → содержимое
     *
     * Почему форматируем сами, а не в logback.xml pattern?
     * Потому что у нас Logstash JSON encoder — он не использует pattern.
     * А мы хотим читаемый вывод в любом случае.
     */
    private String format(String level, String message, Object... args) {
        // Подставляем аргументы в плейсхолдеры {}
        String resolved = replacePlaceholders(message, args);

        // Thread.currentThread().getName()  → "TestNG-method-3" (имя потока)
        // Thread.currentThread().threadId() → 42 (числовой ID)
        // В многопоточном запуске сразу видно какой поток что делает:
        //   [INFO] [TestNG-method-1 #31] → GET /users
        //   [INFO] [TestNG-method-2 #32] → POST /posts
        //   [INFO] [TestNG-method-3 #33] → DELETE /posts/1
        return String.format("[%s] [%s] [%s #%d] → %s",
                LocalDateTime.now().format(TIME_FORMAT),   // время
                level,                                       // уровень
                Thread.currentThread().getName(),            // имя потока
                Thread.currentThread().getId(),              // числовой ID потока
                resolved);                                   // содержимое
    }

    /**
     * Заменяет {} на значения аргументов — аналог SLF4J placeholders.
     *
     * SLF4J делает это сам, но мы форматируем строку ДО передачи в SLF4J,
     * поэтому нужна своя подстановка.
     *
     * Пример: replacePlaceholders("status={} time={}ms", 200, 45)
     *       → "status=200 time=45ms"
     */
    private String replacePlaceholders(String template, Object... args) {
        if (args == null || args.length == 0) return template;

        StringBuilder sb = new StringBuilder();
        int argIndex = 0;
        int i = 0;

        while (i < template.length()) {
            // Ищем пару символов {}
            if (i < template.length() - 1
                    && template.charAt(i) == '{'
                    && template.charAt(i + 1) == '}'
                    && argIndex < args.length) {

                // Заменяем {} на значение аргумента
                sb.append(args[argIndex] != null ? args[argIndex].toString() : "null");
                argIndex++;
                i += 2; // перепрыгиваем оба символа { и }
            } else {
                sb.append(template.charAt(i));
                i++;
            }
        }

        return sb.toString();
    }
}