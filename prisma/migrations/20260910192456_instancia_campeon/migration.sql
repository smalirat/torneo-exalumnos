-- CreateEnum
CREATE TYPE "InstanciaCampeon" AS ENUM ('APERTURA', 'CLAUSURA', 'FINAL_ANUAL', 'DESCONOCIDO');

-- AlterTable
ALTER TABLE "Campeon" ADD COLUMN "instancia" "InstanciaCampeon" NOT NULL DEFAULT 'DESCONOCIDO';

-- Backfill: las filas cargadas a mano heredan la instancia de su torneo real
UPDATE "Campeon" AS c SET "instancia" = t."nombre"::text::"InstanciaCampeon"
FROM "Categoria" AS cat JOIN "Torneo" AS t ON t."id" = cat."torneoId"
WHERE c."categoriaId" = cat."id";

-- DropIndex
DROP INDEX "Campeon_anio_categoriaId_key";

-- CreateIndex
CREATE UNIQUE INDEX "Campeon_anio_categoriaId_instancia_key" ON "Campeon"("anio", "categoriaId", "instancia");
