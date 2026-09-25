# Travel Agent

Spring Boot 4.1 (Java 25) backend with a React + Vite + TypeScript frontend, in a single Maven project.
Spring AI 2.0 (OpenAI starter) powers a travel-agent chat at `POST /api/chat`; the React UI is a chat window.

## Layout

- `src/main/java` – Spring Boot app (REST APIs under `/api`)
- `frontend/` – React app; built by Maven and served from the jar at `/`

## Configure

Export your OpenAI key before starting the app (it is read from the environment, never stored in the repo):

```sh
export OPENAI_API_KEY=sk-...
export OPENAI_MODEL=gpt-5-mini   # optional, this is the default
```

## Run

Full build (downloads Node into `target/`, builds the frontend, packages everything into one jar):

```sh
./mvnw package
java -jar target/travel-agent-0.0.1-SNAPSHOT.jar   # http://localhost:8080
```

Development with hot reload:

```sh
./mvnw spring-boot:run -Dskip.npm -Dskip.installnodenpm   # backend on :8080
cd frontend && npm run dev                                # frontend on :5173, proxies /api to :8080
```

## Chat API

```sh
curl -X POST localhost:8080/api/chat -H 'Content-Type: application/json' \
  -d '{"message":"Plan a weekend in Goa"}'
# -> {"conversationId":"…","reply":"…"}
```

Send the returned `conversationId` with follow-up messages to keep context (the last 20 messages are kept in memory).
