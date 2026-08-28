import { Router } from 'express';

// Cada módulo (temporadas, torneos, partidos, standings, etc.) expone su
// propio router en modules/<nombre>/<nombre>.routes.ts. Acá solo los
// agregamos. Se va completando a medida que agreguemos cada módulo.
export const router = Router();

import { standingsRouter } from './standings/standings.routes';
router.use('/standings', standingsRouter);

import { partidosRouter } from './partidos/partidos.routes';
router.use('/partidos', partidosRouter);

import { temporadasRouter } from './temporadas/temporadas.routes';
router.use('/temporadas', temporadasRouter);

import { torneosRouter } from './torneos/torneos.routes';
router.use('/torneos', torneosRouter);

import { categoriasRouter } from './categorias/categorias.routes';
router.use('/categorias', categoriasRouter);

import { zonasRouter } from './zonas/zonas.routes';
router.use('/zonas', zonasRouter);

import { equiposRouter } from './equipos/equipos.routes';
router.use('/equipos', equiposRouter);

import { jugadoresRouter } from './jugadores/jugadores.routes';
router.use('/jugadores', jugadoresRouter);

import { inscripcionesRouter } from './inscripciones/inscripciones.routes';
router.use('/inscripciones', inscripcionesRouter);

import { authRouter } from './auth/auth.routes';
router.use('/auth', authRouter);

import { usuariosRouter } from './usuarios/usuarios.routes';
router.use('/usuarios', usuariosRouter);

import { goleadoresRouter } from './goleadores/goleadores.routes';
router.use('/goleadores', goleadoresRouter);

import { figurasRouter } from './figuras/figuras.routes';
router.use('/figuras', figurasRouter);

import { imbatiblesRouter } from './imbatibles/imbatibles.routes';
router.use('/imbatibles', imbatiblesRouter);

import { sancionesRouter } from './sanciones/sanciones.routes';
router.use('/sanciones', sancionesRouter);

import { sancionadosRouter } from './sanciones/sancionados.routes';
router.use('/sancionados', sancionadosRouter);

import { campeonesRouter, historialCampeonesRouter } from './campeones/campeones.routes';
router.use('/campeones', campeonesRouter);
router.use('/historial-campeones', historialCampeonesRouter);

import { excelImportRouter } from './excel-import/excelImport.routes';
router.use('/torneos', excelImportRouter); // agrega POST /torneos/:id/importar-excel al mismo router base

// Se va completando a medida que agreguemos cada módulo:
// import { fixtureRouter } from './fixture/fixture.routes';
// router.use('/fixture', fixtureRouter);
