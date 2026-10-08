const form = document.getElementById("summary-form");
const input = document.getElementById("url");
const button = document.getElementById("submit-button");
const errorBox = document.getElementById("error");
const loading = document.getElementById("loading");
const result = document.getElementById("result");
const emptyState = document.getElementById("empty-state");

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (button.disabled) return;

    const url = input.value.trim();
    errorBox.hidden = true;
    result.hidden = true;
    emptyState.hidden = true;

    try {
        const parsed = new URL(url);
        if (!["http:", "https:"].includes(parsed.protocol)) throw new Error();
    } catch {
        errorBox.textContent = "Enter a valid URL starting with http:// or https://.";
        errorBox.hidden = false;
        return;
    }

    setLoading(true);
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 150_000);

    try {
        const response = await fetch("/api/summarize", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ url }),
            signal: controller.signal
        });
        const data = await response.json().catch(() => {
            throw new Error("The server returned an unreadable response. Please try again.");
        });
        if (!response.ok) throw new Error(data.error || "Could not summarize this page. Please try again.");

        document.getElementById("result-heading").textContent = data.title;
        document.getElementById("summary-text").textContent = data.summary;
        const source = document.getElementById("source-link");
        source.href = data.url;
        source.textContent = data.url;
        document.getElementById("truncated-note").hidden = !data.truncated;
        result.hidden = false;
        result.focus({ preventScroll: true });
    } catch (error) {
        errorBox.textContent = error.name === "AbortError"
            ? "This request took too long. Try again or use a different page."
            : error instanceof TypeError
                ? "Cannot reach the server. Check your connection and try again."
                : error.message;
        errorBox.hidden = false;
    } finally {
        clearTimeout(timeout);
        setLoading(false);
    }
});

function setLoading(isLoading) {
    button.disabled = isLoading;
    input.disabled = isLoading;
    button.textContent = isLoading ? "Loading..." : "Summarize →";
    loading.hidden = !isLoading;
    form.setAttribute("aria-busy", String(isLoading));
}
