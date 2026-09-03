-- CreateEnum
CREATE TYPE "NombreTorneo" AS ENUM ('APERTURA', 'CLAUSURA');

-- CreateEnum
CREATE TYPE "NombreCategoria" AS ENUM ('A', 'B', 'C');

-- CreateEnum
CREATE TYPE "EstadoPartido" AS ENUM ('PENDIENTE', 'JUGADO', 'SUSPENDIDO');

-- CreateEnum
CREATE TYPE "EstadoImportacion" AS ENUM ('PENDIENTE', 'PROCESANDO', 'COMPLETADO', 'ERROR');

-- CreateEnum
CREATE TYPE "RolUsuario" AS ENUM ('ADMIN', 'DELEGADO');

-- CreateTable
CREATE TABLE "Temporada" (
    "id" SERIAL NOT NULL,
    "anio" INTEGER NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Temporada_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Torneo" (
    "id" SERIAL NOT NULL,
    "temporadaId" INTEGER NOT NULL,
    "nombre" "NombreTorneo" NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Torneo_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Categoria" (
    "id" SERIAL NOT NULL,
    "torneoId" INTEGER NOT NULL,
    "nombre" "NombreCategoria" NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Categoria_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Zona" (
    "id" SERIAL NOT NULL,
    "categoriaId" INTEGER NOT NULL,
    "nombre" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Zona_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Equipo" (
    "id" SERIAL NOT NULL,
    "nombre" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Equipo_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "InscripcionEquipo" (
    "id" SERIAL NOT NULL,
    "equipoId" INTEGER NOT NULL,
    "categoriaId" INTEGER NOT NULL,
    "zonaId" INTEGER,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "InscripcionEquipo_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Jugador" (
    "id" SERIAL NOT NULL,
    "nombre" TEXT NOT NULL,
    "equipoId" INTEGER,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Jugador_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Partido" (
    "id" SERIAL NOT NULL,
    "categoriaId" INTEGER NOT NULL,
    "zonaId" INTEGER,
    "equipoLocalId" INTEGER NOT NULL,
    "equipoVisitanteId" INTEGER NOT NULL,
    "golesLocal" INTEGER,
    "golesVisitante" INTEGER,
    "fecha" TIMESTAMP(3) NOT NULL,
    "jornada" INTEGER NOT NULL,
    "campo" TEXT,
    "cancha" TEXT,
    "horario" TEXT,
    "estado" "EstadoPartido" NOT NULL DEFAULT 'PENDIENTE',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Partido_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "PuntosRestados" (
    "id" SERIAL NOT NULL,
    "equipoId" INTEGER NOT NULL,
    "categoriaId" INTEGER NOT NULL,
    "zonaId" INTEGER,
    "puntos" INTEGER NOT NULL,
    "motivo" TEXT,
    "fecha" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "PuntosRestados_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Goleador" (
    "id" SERIAL NOT NULL,
    "jugadorId" INTEGER NOT NULL,
    "torneoId" INTEGER NOT NULL,
    "goles" INTEGER NOT NULL DEFAULT 0,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Goleador_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Figura" (
    "id" SERIAL NOT NULL,
    "jugadorId" INTEGER NOT NULL,
    "torneoId" INTEGER NOT NULL,
    "veces" INTEGER NOT NULL DEFAULT 0,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Figura_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Imbatible" (
    "id" SERIAL NOT NULL,
    "jugadorId" INTEGER NOT NULL,
    "torneoId" INTEGER NOT NULL,
    "golesRecibidos" INTEGER NOT NULL DEFAULT 0,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Imbatible_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Sancion" (
    "id" SERIAL NOT NULL,
    "jugadorId" INTEGER NOT NULL,
    "equipoId" INTEGER NOT NULL,
    "torneoId" INTEGER NOT NULL,
    "tipoTarjeta" TEXT NOT NULL,
    "fechasSuspension" INTEGER NOT NULL,
    "cumplida" BOOLEAN NOT NULL DEFAULT false,
    "pendiente" BOOLEAN NOT NULL DEFAULT true,
    "observaciones" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Sancion_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Campeon" (
    "id" SERIAL NOT NULL,
    "anio" INTEGER NOT NULL,
    "categoriaId" INTEGER NOT NULL,
    "equipoId" INTEGER NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "Campeon_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ImportacionExcel" (
    "id" SERIAL NOT NULL,
    "nombreArchivo" TEXT NOT NULL,
    "rutaArchivo" TEXT NOT NULL,
    "fechaCarga" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "torneoId" INTEGER NOT NULL,
    "estado" "EstadoImportacion" NOT NULL DEFAULT 'PENDIENTE',
    "detalleError" TEXT,
    "filasCreadas" INTEGER,
    "filasActualizadas" INTEGER,

    CONSTRAINT "ImportacionExcel_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Usuario" (
    "id" SERIAL NOT NULL,
    "username" TEXT NOT NULL,
    "passwordHash" TEXT NOT NULL,
    "rol" "RolUsuario" NOT NULL,
    "equipoId" INTEGER,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "Usuario_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "Temporada_anio_key" ON "Temporada"("anio");

-- CreateIndex
CREATE UNIQUE INDEX "Torneo_temporadaId_nombre_key" ON "Torneo"("temporadaId", "nombre");

-- CreateIndex
CREATE UNIQUE INDEX "Categoria_torneoId_nombre_key" ON "Categoria"("torneoId", "nombre");

-- CreateIndex
CREATE UNIQUE INDEX "Zona_categoriaId_nombre_key" ON "Zona"("categoriaId", "nombre");

-- CreateIndex
CREATE UNIQUE INDEX "Equipo_nombre_key" ON "Equipo"("nombre");

-- CreateIndex
CREATE UNIQUE INDEX "InscripcionEquipo_equipoId_categoriaId_zonaId_key" ON "InscripcionEquipo"("equipoId", "categoriaId", "zonaId");

-- CreateIndex
CREATE INDEX "Partido_categoriaId_zonaId_jornada_idx" ON "Partido"("categoriaId", "zonaId", "jornada");

-- CreateIndex
CREATE UNIQUE INDEX "Partido_fecha_equipoLocalId_equipoVisitanteId_key" ON "Partido"("fecha", "equipoLocalId", "equipoVisitanteId");

-- CreateIndex
CREATE UNIQUE INDEX "Goleador_jugadorId_torneoId_key" ON "Goleador"("jugadorId", "torneoId");

-- CreateIndex
CREATE UNIQUE INDEX "Figura_jugadorId_torneoId_key" ON "Figura"("jugadorId", "torneoId");

-- CreateIndex
CREATE UNIQUE INDEX "Imbatible_jugadorId_torneoId_key" ON "Imbatible"("jugadorId", "torneoId");

-- CreateIndex
CREATE UNIQUE INDEX "Campeon_anio_categoriaId_key" ON "Campeon"("anio", "categoriaId");

-- CreateIndex
CREATE UNIQUE INDEX "Usuario_username_key" ON "Usuario"("username");

-- AddForeignKey
ALTER TABLE "Torneo" ADD CONSTRAINT "Torneo_temporadaId_fkey" FOREIGN KEY ("temporadaId") REFERENCES "Temporada"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Categoria" ADD CONSTRAINT "Categoria_torneoId_fkey" FOREIGN KEY ("torneoId") REFERENCES "Torneo"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Zona" ADD CONSTRAINT "Zona_categoriaId_fkey" FOREIGN KEY ("categoriaId") REFERENCES "Categoria"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "InscripcionEquipo" ADD CONSTRAINT "InscripcionEquipo_equipoId_fkey" FOREIGN KEY ("equipoId") REFERENCES "Equipo"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "InscripcionEquipo" ADD CONSTRAINT "InscripcionEquipo_categoriaId_fkey" FOREIGN KEY ("categoriaId") REFERENCES "Categoria"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "InscripcionEquipo" ADD CONSTRAINT "InscripcionEquipo_zonaId_fkey" FOREIGN KEY ("zonaId") REFERENCES "Zona"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Jugador" ADD CONSTRAINT "Jugador_equipoId_fkey" FOREIGN KEY ("equipoId") REFERENCES "Equipo"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Partido" ADD CONSTRAINT "Partido_categoriaId_fkey" FOREIGN KEY ("categoriaId") REFERENCES "Categoria"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Partido" ADD CONSTRAINT "Partido_zonaId_fkey" FOREIGN KEY ("zonaId") REFERENCES "Zona"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Partido" ADD CONSTRAINT "Partido_equipoLocalId_fkey" FOREIGN KEY ("equipoLocalId") REFERENCES "Equipo"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Partido" ADD CONSTRAINT "Partido_equipoVisitanteId_fkey" FOREIGN KEY ("equipoVisitanteId") REFERENCES "Equipo"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "PuntosRestados" ADD CONSTRAINT "PuntosRestados_equipoId_fkey" FOREIGN KEY ("equipoId") REFERENCES "Equipo"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "PuntosRestados" ADD CONSTRAINT "PuntosRestados_categoriaId_fkey" FOREIGN KEY ("categoriaId") REFERENCES "Categoria"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "PuntosRestados" ADD CONSTRAINT "PuntosRestados_zonaId_fkey" FOREIGN KEY ("zonaId") REFERENCES "Zona"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Goleador" ADD CONSTRAINT "Goleador_jugadorId_fkey" FOREIGN KEY ("jugadorId") REFERENCES "Jugador"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Goleador" ADD CONSTRAINT "Goleador_torneoId_fkey" FOREIGN KEY ("torneoId") REFERENCES "Torneo"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Figura" ADD CONSTRAINT "Figura_jugadorId_fkey" FOREIGN KEY ("jugadorId") REFERENCES "Jugador"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Figura" ADD CONSTRAINT "Figura_torneoId_fkey" FOREIGN KEY ("torneoId") REFERENCES "Torneo"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Imbatible" ADD CONSTRAINT "Imbatible_jugadorId_fkey" FOREIGN KEY ("jugadorId") REFERENCES "Jugador"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Imbatible" ADD CONSTRAINT "Imbatible_torneoId_fkey" FOREIGN KEY ("torneoId") REFERENCES "Torneo"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Sancion" ADD CONSTRAINT "Sancion_jugadorId_fkey" FOREIGN KEY ("jugadorId") REFERENCES "Jugador"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Sancion" ADD CONSTRAINT "Sancion_equipoId_fkey" FOREIGN KEY ("equipoId") REFERENCES "Equipo"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Sancion" ADD CONSTRAINT "Sancion_torneoId_fkey" FOREIGN KEY ("torneoId") REFERENCES "Torneo"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Campeon" ADD CONSTRAINT "Campeon_categoriaId_fkey" FOREIGN KEY ("categoriaId") REFERENCES "Categoria"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Campeon" ADD CONSTRAINT "Campeon_equipoId_fkey" FOREIGN KEY ("equipoId") REFERENCES "Equipo"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ImportacionExcel" ADD CONSTRAINT "ImportacionExcel_torneoId_fkey" FOREIGN KEY ("torneoId") REFERENCES "Torneo"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Usuario" ADD CONSTRAINT "Usuario_equipoId_fkey" FOREIGN KEY ("equipoId") REFERENCES "Equipo"("id") ON DELETE SET NULL ON UPDATE CASCADE;
