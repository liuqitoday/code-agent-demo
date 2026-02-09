# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Code Agent Demo is a Spring AI-based code generation agent demonstrating LLM tool-calling patterns. It's an educational CLI application that uses the ReAct pattern to generate code files via natural language.

**Tech Stack:** Java 17, Spring Boot 3.5.9, Spring AI 1.1.2, Spring Shell 3.4.1

## Build and Run Commands

```bash
# Build
mvn compile

# Run (requires OPENAI_API_KEY environment variable)
mvn spring-boot:run

# Run tests
mvn test

# Run specific test class
mvn test -Dtest=CodeAgentApplicationTests

# Package executable JAR
mvn clean package
java -jar target/code-agent-demo-0.0.1-SNAPSHOT.jar
```

## Environment Variables

```bash
export OPENAI_API_KEY=your-api-key
export OPENAI_BASE_URL=https://api.openai.com  # optional, for compatible APIs
export OPENAI_MODEL=gpt-4                       # optional
export AGENT_WORKSPACE=./workspace              # optional, where generated files go
```

## Architecture

```
User Input → AgentCommands (Shell) → CodeAgent → ChatClient (Spring AI) → LLM
                                         ↓
                                   MessageChatMemoryAdvisor (auto manages history)
                                         ↓
                                   CodeAgentTools (@Tool methods)
                                         ↓
                                   File System (workspace/)
```

**Key modules:**
- `agent/` - Core orchestration: `CodeAgent` coordinates LLM + tools using Spring AI ChatClient, `AgentResponse` wraps results
- `tool/` - `CodeAgentTools` exposes file operations via `@Tool` annotations (createFile, readFile, editFile, editFileAll, listDirectory, createDirectory)
- `shell/` - `AgentCommands` defines CLI commands (agent, ask, clear, config, guide)
- `config/` - `AiConfig` configures ChatMemory/RestClient/Retry, `LoggingInterceptor` traces HTTP with round-based console output, `LlmRetryListener` logs retry events
- `service/` - `LlmService` encapsulates LLM calls with Spring Retry support
- `util/` - `ErrorMessages` maps error types to user-friendly messages

**Conversation memory:**
- Uses Spring AI's `MessageWindowChatMemory` (sliding window, max 20 messages)
- `MessageChatMemoryAdvisor` automatically manages conversation history
- Supports conversation ID for session isolation

**Execution modes:**
- Agent mode (`agent`): Blocking, with tool calling — uses `chatClient` with registered tools
- Ask mode (`ask`): Streaming, no tools — uses separate `streamClient` to avoid Spring AI stream + tool calling bug ([#5167](https://github.com/spring-projects/spring-ai/issues/5167)), falls back to blocking on failure

## Shell Commands

- `agent`/`ag`/`a "prompt"` - Agent mode: tool calling enabled, can create/edit/read files (supports `-f folder`)
- `ask`/`q "question"` - Ask mode: streaming Q&A, no tool calling
- `clear`/`c` - Clear conversation history and start new session
- `config`/`cfg` - Show current configuration (includes session ID)
- `guide` - Usage help

## Key Implementation Details

- Spring AI handles the ReAct loop automatically via `@Tool`-annotated methods
- Tool calling only works in blocking mode (agent command); streaming mode (ask command) uses a separate ChatClient without tools
- Retry logic: 3 retries with exponential backoff (2s, 4s, 8s, max 10s) via Spring Retry
- Path validation in tools prevents directory traversal outside workspace
- Logs written to `logs/code-agent.log` (DEBUG level for project code)
- Each session has a unique conversation ID (e.g., `session-a1b2c3d4`)

## Documentation

- `docs/agent-design.md` - Comprehensive Agent design principles (Chinese)
- `docs/LLM_LOGGING_GUIDE.md` - HTTP logging and debugging guide
