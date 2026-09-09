/*
  Warnings:

  - You are about to drop the column `cumplida` on the `Sancion` table. All the data in the column will be lost.
  - You are about to drop the column `pendiente` on the `Sancion` table. All the data in the column will be lost.

*/
-- CreateEnum
CREATE TYPE "EstadoSancion" AS ENUM ('PENDIENTE', 'CUMPLIDA', 'EN_TRIBUNAL');

-- AlterTable
ALTER TABLE "Sancion" DROP COLUMN "cumplida",
DROP COLUMN "pendiente",
ADD COLUMN     "estado" "EstadoSancion" NOT NULL DEFAULT 'PENDIENTE';
