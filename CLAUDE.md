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
- `agent/` - Core orchestration: `CodeAgent` coordinates LLM + tools using Spring AI ChatClient
- `tool/` - `CodeAgentTools` exposes file operations via `@Tool` annotations (createFile, readFile, listDirectory, createDirectory)
- `shell/` - `AgentCommands` defines CLI commands (generate, ask, query, clear, config, guide)
- `config/` - `AiConfig` configures ChatMemory and RestClient, `LoggingInterceptor` traces HTTP

**Conversation memory:**
- Uses Spring AI's `MessageWindowChatMemory` (sliding window, max 20 messages)
- `MessageChatMemoryAdvisor` automatically manages conversation history
- Supports conversation ID for session isolation

**Execution modes:**
- Blocking mode (`generate`, `query`): Required for tool calling to work correctly
- Streaming mode (`ask`): For text-only responses, falls back to blocking on failure

## Shell Commands

- `generate`/`gen`/`g "prompt"` - Generate code with tool calling (files saved to workspace)
- `ask`/`a "question"` - Q&A with streaming
- `query`/`q "question"` - Q&A without streaming (more stable)
- `clear`/`c` - Clear conversation history and start new session
- `config`/`cfg` - Show current configuration (includes session ID)
- `guide` - Usage help

## Key Implementation Details

- Spring AI handles the ReAct loop automatically via `@Tool`-annotated methods
- Tool calling only works in blocking mode (stream mode has null toolName issues)
- Retry logic: 3 attempts with exponential backoff (2s, 4s, 6s) for timeouts
- Path validation in tools prevents directory traversal outside workspace
- Logs written to `logs/code-agent.log` (DEBUG level for project code)
- Each session has a unique conversation ID (e.g., `session-a1b2c3d4`)

## Documentation

- `docs/agent-design.md` - Comprehensive Agent design principles (Chinese)
- `docs/LLM_LOGGING_GUIDE.md` - HTTP logging and debugging guide
