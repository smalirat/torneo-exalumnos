-- Rename PuntosRestados -> PuntosPresentismo (PR es presentismo: suma, no resta)
ALTER TABLE "PuntosRestados" RENAME TO "PuntosPresentismo";
ALTER TABLE "PuntosPresentismo" RENAME CONSTRAINT "PuntosRestados_pkey" TO "PuntosPresentismo_pkey";
ALTER TABLE "PuntosPresentismo" RENAME CONSTRAINT "PuntosRestados_equipoId_fkey" TO "PuntosPresentismo_equipoId_fkey";
ALTER TABLE "PuntosPresentismo" RENAME CONSTRAINT "PuntosRestados_categoriaId_fkey" TO "PuntosPresentismo_categoriaId_fkey";
ALTER TABLE "PuntosPresentismo" RENAME CONSTRAINT "PuntosRestados_zonaId_fkey" TO "PuntosPresentismo_zonaId_fkey";
