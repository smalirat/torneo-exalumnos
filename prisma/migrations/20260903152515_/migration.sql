-- CreateTable
CREATE TABLE "Amonestacion" (
    "id" SERIAL NOT NULL,
    "jugadorId" INTEGER NOT NULL,
    "equipoId" INTEGER NOT NULL,
    "torneoId" INTEGER NOT NULL,
    "cantidad" INTEGER NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Amonestacion_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "Amonestacion_torneoId_equipoId_idx" ON "Amonestacion"("torneoId", "equipoId");

-- CreateIndex
CREATE UNIQUE INDEX "Amonestacion_jugadorId_torneoId_key" ON "Amonestacion"("jugadorId", "torneoId");

-- AddForeignKey
ALTER TABLE "Amonestacion" ADD CONSTRAINT "Amonestacion_jugadorId_fkey" FOREIGN KEY ("jugadorId") REFERENCES "Jugador"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Amonestacion" ADD CONSTRAINT "Amonestacion_equipoId_fkey" FOREIGN KEY ("equipoId") REFERENCES "Equipo"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Amonestacion" ADD CONSTRAINT "Amonestacion_torneoId_fkey" FOREIGN KEY ("torneoId") REFERENCES "Torneo"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
