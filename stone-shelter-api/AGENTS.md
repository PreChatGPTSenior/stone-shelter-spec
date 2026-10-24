# Backend execution rules

Read ../AGENTS.md, the assigned OpenSpec change and ticket packet first.
Follow AGENTS_CODE_CONVENTION.md for Java syntax and mapping conventions; where historical workflow links disagree, the new root instructions and assigned packet control.
Java 23.0.2, Boot 4.1.1 and Spring AI 2.0.1 remain pinned until a reviewed version ADR changes them.
Controllers own HTTP; services own business transactions; repositories own queries; dedicated mappers own structured conversion; DTOs live in shared.
Use Liquibase append-only migrations and PostgreSQL Testcontainers; never H2.
LLM chat, embeddings, retrieval and RAG use Spring AI: ChatClient, EmbeddingModel, PgVectorStore and RetrievalAugmentationAdvisor. No custom Ollama HTTP inference or hand-written vector similarity SQL after the migration tickets. Exact relational catalog queries remain repository queries.
Run only ticket-selected tests plus mandatory static/architecture gates. A green seed test is not acceptance of the new behavior.
