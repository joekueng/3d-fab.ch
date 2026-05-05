# AGENTS.md - Global Project Instructions

This file provides high-level guidance for AI agents working on the **Print Calculator** project. It complements the more detailed `GEMINI.md` and the directory-specific `AGENTS.md` files.

## Project Overview
**Print Calculator** is an end-to-end 3D printing platform.
- **Core Engine**: Real-time slicing using headless OrcaSlicer for precise time/material estimation.
- **Shop**: E-commerce system for 3D printed products and filaments.
- **QR Tracking**: Analytics for physical product tracking via QR codes.
- **Swiss Integration**: Supports TWINT payments and Swiss QR-Bill invoicing.

## Tech Stack
- **Backend**: Java 21, Spring Boot 3.4, PostgreSQL, Flyway, Hibernate.
- **Frontend**: Angular 19 (Standalone), Angular Material, Three.js.
- **DevOps**: Docker, Gitea Actions, Swiss-based deployment.

## Global Agent Rules

### 1. Architectural Integrity
- **Follow Established Patterns**: Stick to the Service-Repository-Controller pattern in the backend and the Features-Shared-Core structure in the frontend.
- **No Inline Code**: In Angular, always separate HTML, SCSS, and TypeScript files.
- **DRY (Don't Repeat Yourself)**: Before implementing a new utility or UI component, check if it already exists in `backend/src/main/java/.../util` or `frontend/src/app/shared`.

### 2. Implementation Standards
- **Validation**: Always use `@Valid` and Bean Validation constraints for DTOs.
- **Type Safety**: Avoid `any` in TypeScript. Use explicit interfaces and types.
- **Security**: Never expose sensitive data. Use session-based auth for admin and CSRF protection.
- **Performance**: Be mindful of expensive operations like real-time slicing or heavy 3D model processing. Use async events where appropriate.

### 3. Documentation & Communication
- **Update GEMINI.md**: If you introduce a significant architectural change, update the root `GEMINI.md`.
- **Commit Messages**: Keep commits atomic and descriptive. Reference modules (e.g., `feat(shop): add product variants`).
- **Private Memory**: Use the project's private memory for local/personal notes that shouldn't be committed.

## Navigation & Entry Points

### Backend (`/backend`)
- **Services**: `src/main/java/com/printcalculator/service/` (organized by module: `quote`, `shop`, `qr`, etc.)
- **Controllers**: `src/main/java/com/printcalculator/controller/` (Public and Admin subpackages)
- **Data Access**: `src/main/java/com/printcalculator/repository/` and `src/main/java/com/printcalculator/entity/`
- **DTOs & Models**: `src/main/java/com/printcalculator/dto/` and `src/main/java/com/printcalculator/model/`
- **Database Migrations**: `src/main/resources/db/migration/`
- **Slicing Logic**: `SlicerService.java` (usually in `service/quote` or `service/`) and `OrcaSlicer` integration.

### Frontend (`/frontend`)
- **Shared Components**: `src/app/shared/components/`
- **UI Primitives**: `src/styles/_ui.scss`
- **Feature Modules**: `src/app/features/` (e.g., `calculator`, `shop`, `admin`).
- **Refer to `frontend/AGENTS.md`** for detailed UI/UX reuse rules and styling constraints.

## Core Workflows for Agents

### Adding a New Feature
1. **Research**: Check existing services and DTOs.
2. **Backend**: Create Entity -> Repository -> Service -> Controller. Add Flyway migration.
3. **Frontend**: Create Service -> Component. Reuse `app-*` shared components and `ui-*` primitives.
4. **Validation**: Add unit tests for logic and integration tests for API endpoints.

### Modifying Slicing/Quote Logic
- This is the "brain" of the application. Extreme care is required.
- Check `PricingPolicy` and `SlicerService`.
- Always verify that changes don't break existing quote calculations.

### UI/UX Updates
- **Mandatory**: Read `frontend/AGENTS.md`.
- Do not introduce new design systems. Use the existing SCSS variables and primitives.
- Ensure responsiveness across mobile and desktop.

## Tooling Context
- **OrcaSlicer**: Must be available for backend tasks involving G-Code generation.
- **FFmpeg**: Used for media processing.
- **Three.js**: Used for the `stl-viewer` component.
