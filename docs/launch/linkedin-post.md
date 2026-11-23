# LinkedIn post: draft (not published)

Attach screenshots of the redesigned app (to be captured after the visual redesign).
For a carousel, use this order: board, analysis, match, letter.

---

## English

I built ApplyPilot: a job-application tracker where a small team of AI agents matches your
CV to each job posting and drafts a cover letter, and I tried to make it honest.

What it does:
• A Kanban board for your applications
• Paste a job posting: an LLM extracts skills, seniority and salary, validated against a schema
• One click: a Matcher looks things up in your CV through tool calls and quotes its evidence,
  a Writer drafts the letter in the posting's language, and a Reviewer sends unsupported
  claims back (2 revisions at most)
• An MCP server, so Claude can read your board and matches with a read-only token

What I learned building it:
🔍 Let the model judge, but verify in code. Every quote the Matcher cites must exist in the
CV, the score is arithmetic, and a letter with a number the CV doesn't contain can't be
approved.
🧪 Evals find what unit tests can't. My first eval run (12 labelled cases, English and
Spanish) caught a prompt injection that reached a letter, invented percentages in 7 of 12
drafts, and a provider error that broke matching. All of it passed the unit tests. After the
fixes: 0 leaks, 0 invented numbers.
🛡️ Treat postings and CVs as untrusted input. Tags they can't close, instructions only in
the system prompt, structured outputs, and the letter's language decided by code, because
a posting told the model to switch languages and it did.
💸 A demo on free tiers needs design: an atomic daily AI quota, closed registration with a
demo account, and a "server is waking up" notice for cold starts.

Stack: Java 21 · Spring Boot 4 · Spring AI · PostgreSQL · Angular 22 · Testcontainers ·
GitHub Actions · Docker (Render) · Neon · Vercel · Groq

Try it (no sign-up, fictitious data): https://applypilot-wga.vercel.app
Code and a stage-by-stage learning log: https://github.com/gonzalo1905/applypilot

#Java #SpringBoot #Angular #LLM #AIAgents #MCP #SoftwareEngineering

---

## Español

Construí ApplyPilot: un tracker de postulaciones donde un pequeño equipo de agentes de IA
compara tu CV con cada aviso y redacta una carta de presentación. Intenté que fuera honesto.

Qué hace:
• Un tablero Kanban para tus postulaciones
• Pegás un aviso y un LLM extrae skills, seniority y salario, validados contra un schema
• Con un clic: un Matcher consulta tu CV con tool calls y cita la evidencia, un Writer
  redacta la carta en el idioma del aviso y un Reviewer devuelve lo que el CV no respalda
  (2 revisiones como máximo)
• Un servidor MCP, para que Claude lea tu tablero y tus matches con un token de solo lectura

Lo que aprendí construyéndolo:
🔍 Que el modelo juzgue, pero que el código verifique. Cada cita del Matcher tiene que
existir en el CV, el puntaje es aritmético y una carta con un número que el CV no tiene no
se puede aprobar.
🧪 Las evals encuentran lo que los tests unitarios no ven. Mi primera corrida (12 casos
etiquetados, en inglés y español) encontró un prompt injection que llegó a una carta,
porcentajes inventados en 7 de 12 borradores y un error del proveedor que rompía el
matching. Todo eso pasaba los tests unitarios. Después de los arreglos: 0 fugas y 0 números
inventados.
🛡️ Avisos y CVs son input no confiable. Tags que no pueden cerrar, instrucciones solo en el
system prompt, salidas estructuradas y el idioma de la carta decidido por código, porque un
aviso le pidió al modelo cambiar de idioma y lo hizo.
💸 Un demo en planes gratis también se diseña: una cuota diaria de IA atómica, registro
cerrado con una cuenta demo y un aviso de "el servidor está despertando" para los
arranques en frío.

Stack: Java 21 · Spring Boot 4 · Spring AI · PostgreSQL · Angular 22 · Testcontainers ·
GitHub Actions · Docker (Render) · Neon · Vercel · Groq

Probalo (sin registro, datos ficticios): https://applypilot-wga.vercel.app
Código y bitácora de aprendizaje etapa por etapa: https://github.com/gonzalo1905/applypilot

#Java #SpringBoot #Angular #LLM #AIAgents #MCP #DesarrolloDeSoftware
