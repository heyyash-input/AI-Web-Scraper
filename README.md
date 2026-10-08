# AI Web Scraper

A small Spring Boot app that reads a public webpage and returns a short AI summary. The frontend uses plain HTML, CSS, and JavaScript, with the black-and-yellow styling from my [earlier web scraper](https://github.com/heyyash-input/webscraper-springboot).

## Stack

- Java 21 and Spring Boot 4
- Jsoup for extracting text from HTML
- Apache HttpClient for fetching pages with connection-level public-address checks
- Groq's API for summarization
- HTML, CSS, and JavaScript served by Spring Boot

No database, Node.js, or separate frontend server is needed.

## Run locally

Install **JDK 21** and make sure `java -version` works. The included Maven wrapper downloads Maven on its first run, so a separate Maven installation is optional. Internet access is required to download dependencies, fetch webpages, and call Groq.

### 1. Configure the API key

Create a free Groq account and generate a key at [console.groq.com/keys](https://console.groq.com/keys). Keep the account on the Free plan. Requests are subject to Groq's [free-plan limits](https://console.groq.com/docs/rate-limits).

Copy `.env.example` to **`.env` in the project root, next to `pom.xml`**:

```text
ai-web-scraper/
  .env              <-- put your key here
  .env.example
  pom.xml
  mvnw
  mvnw.cmd
  src/
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

macOS / Linux:

```bash
cp .env.example .env
```

Edit `.env`:

```dotenv
GROQ_API_KEY=your_groq_api_key_here
GROQ_MODEL=openai/gpt-oss-20b
PORT=8080
```

The default is an open-weight model hosted by **Groq**. It uses a Groq key and Groq's free plan; no paid OpenAI API key is used. You can change `GROQ_MODEL` to another text model available to your Groq account.

Spring Boot loads this root `.env` as a properties file. Use `KEY=value` without quotes or `export`. Restart the application after changing it. Environment variables set by your hosting platform override the file. `.env` is ignored by Git and excluded from Docker images.

### 2. Start the backend and frontend

Run from the project root.

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

macOS / Linux:

```bash
sh mvnw spring-boot:run
```

Open **[http://localhost:8080](http://localhost:8080)**. This starts both parts: Spring Boot serves the frontend at `/` and the backend at `/api/summarize`. Do not open `index.html` directly from disk.

Paste a full `https://` URL and click **Summarize**. The button shows **Loading...** while the request runs. A successful result includes the page title, source link, and summary. Invalid URLs, inaccessible pages, missing keys, and AI rate limits show a readable error.

The UI starts without an API key, but generating a summary requires a valid key.

### Tests and production build

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
java -jar target/ai-web-scraper.jar
```

On macOS / Linux, replace `.\mvnw.cmd` with `sh mvnw`. Run the JAR from the project root so it can read `.env`.

Tests cover text extraction, long-page limits, URL validation, redirects to private addresses, API validation, and AI success/error responses. AI calls in tests go to a local mock server, so tests do not need a real key or use your quota.

## API

`POST /api/summarize` with `Content-Type: application/json`:

```json
{"url": "https://example.com/article"}
```

Example response shape (illustrative):

```json
{
  "url": "https://example.com/article",
  "title": "Article title",
  "summary": "A short summary of the article.",
  "truncated": false
}
```

Errors use `{"error": "Readable message"}` with an appropriate HTTP status.

## How it works

1. The frontend sends the URL to the Spring Boot controller.
2. The scraper accepts HTTP/HTTPS URLs on standard web ports, checks the resolved addresses, and validates each redirect. The HTTP client also checks DNS addresses when connecting.
3. Jsoup removes scripts, navigation, footers, and other non-content elements. It prefers `article` or `main` content and falls back to the body.
4. Up to 12,000 characters of extracted text are sent to Groq with a prompt requesting a short summary.
5. The frontend displays the response as plain text.

The backend keeps the API key private. No scraped pages or summaries are stored.

## Deploy on Render

The included `Dockerfile` builds and runs the entire app as one service.

1. Push this project to your public GitHub repository.
2. In Render, create a **Web Service** and connect that repository.
3. Choose the **Docker** runtime. Use the repository root and `./Dockerfile`.
4. Set `GROQ_API_KEY` in Render's environment settings. Optionally set `GROQ_MODEL`; the default is `openai/gpt-oss-20b`. The app reads the platform's `PORT` variable.
5. Deploy and open the URL Render assigns. Test one real summary before submitting the link.

See [Render's Docker deployment documentation](https://render.com/docs/docker). Hosting availability and pricing depend on the plan you select. For a Docker build locally:

```bash
docker build -t ai-web-scraper .
docker run --rm -p 8080:8080 --env-file .env ai-web-scraper
```

## Limitations and tradeoffs

- This reads basic HTML. It does not run page JavaScript, log in, bypass paywalls, or bypass bot protections.
- Extraction is a simple heuristic, so pages without clear article markup can include unrelated text.
- Pages larger than 2 MB are rejected. Long extracted text is shortened to limit AI input; the UI tells you when this happens.
- Groq's free tier has quotas. A rate-limit response asks the user to retry later.
- This is a small demo with no authentication or application-level request quotas. Those would be needed for a shared service with significant traffic.

## Implementation challenges

The main challenge is extracting useful text without passing navigation and other page clutter to the model. Removing common non-content elements and preferring `article` or `main` gives a practical starting point. Long pages also need an input limit so a single request does not consume too much of the free API quota. The UI reports both truncated content and provider errors rather than silently returning an incomplete result.
