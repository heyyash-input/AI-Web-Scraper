# AI Web Scraper

Paste a public webpage URL and get a short AI generated summary. The app is built with Java and Spring Boot, with a lightweight HTML, CSS, and JavaScript interface.

**Live app:** [ai-web-scraper-sm87.onrender.com](https://ai-web-scraper-sm87.onrender.com/)

## Features

- Fetches public HTML pages and extracts the main readable text.
- Summarizes the page with Groq's free API tier.
- Shows the page title, source link, loading state, and clear errors.
- Keeps the API key on the server; it is never sent to the browser.
- Limits page downloads and summary input size.
- Blocks private network addresses, including when a page redirects.

## Try the live app

Open the [live app](https://ai-web-scraper-sm87.onrender.com/), paste a complete public URL such as `https://example.com/article`, and select **Summarize**.

The first request can take a little longer if the free Render service has been idle. The deployed service also needs a valid `GROQ_API_KEY` environment variable to generate summaries.

## Run locally

You need **JDK 21**. Maven is included through the wrapper, so a separate Maven install is optional. The first run needs internet access to download dependencies.

### 1. Get a Groq API key

Create a key at [console.groq.com/keys](https://console.groq.com/keys). The default model runs through Groq; no paid OpenAI API key is required. Groq's free usage is subject to [rate limits](https://console.groq.com/docs/rate-limits).

### 2. Create the local environment file

Copy `.env.example` to **`.env` in the repository root, beside `pom.xml`**.

PowerShell:

```powershell
Copy-Item .env.example .env
```

macOS / Linux:

```bash
cp .env.example .env
```

Open `.env` and add your key:

```dotenv
GROQ_API_KEY=paste_your_groq_key_here
GROQ_MODEL=openai/gpt-oss-20b
PORT=8080
```

Keep the key on the right side of `=` without quotes. The `.env` file is ignored by Git. Restart the app after changing it.

### 3. Start the app

Run from the project root.

PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

macOS / Linux:

```bash
sh mvnw spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080). Spring Boot serves the page and API from the same app. Keep the terminal open while you use it.

## Deploy on Render

This repository includes a `Dockerfile` for deploying the Spring Boot app as one public web service.

1. In Render, create a **Web Service** and connect this GitHub repository.
2. Select **Docker** as the runtime. The Dockerfile is in the repository root.
3. Add `GROQ_API_KEY` in the service's **Environment** settings. Add `GROQ_MODEL` only if you want a different model.
4. Deploy the service and open the `onrender.com` URL Render provides.

Set the key in Render's environment settings; don't commit it to GitHub. For details, see [Render's Docker deployment guide](https://render.com/docs/docker).

## API

`POST /api/summarize`

Request body:

```json
{
  "url": "https://example.com/article"
}
```

Successful response:

```json
{
  "url": "https://example.com/article",
  "title": "Example article",
  "summary": "A concise summary of the page.",
  "truncated": false
}
```

If a page has more text than the AI input limit, `truncated` is `true`. Errors return a readable `error` message.

## How it works

1. The browser posts the URL to the Spring Boot API.
2. The backend checks the URL and redirects, then fetches the HTML.
3. Jsoup removes common page clutter and extracts the main text.
4. The extracted text goes to Groq for a short summary.
5. The browser displays the title, source, and summary.

Scraped pages and summaries are not stored.

## Limitations

- The scraper reads basic HTML. It does not run page JavaScript or access pages that require a login.
- Some websites block automated requests, and simple extraction may include unrelated text.
- Downloads over 2 MB are rejected; extracted text is limited to 12,000 characters.
- Groq's free tier has usage limits. If the limit is reached, try again later.
- The public demo has no user accounts or per-user request limits. Avoid sharing it for high-traffic use without adding those controls.

## Tech used

Java 21 · Spring Boot · Jsoup · Apache HttpClient · Groq · HTML · CSS · JavaScript

## Challenge

The main challenge was extracting useful article text without including menus, footers, and other page clutter. Pages can also be very long, so the app limits how much text it sends to the AI service and tells the user when the source was shortened.
