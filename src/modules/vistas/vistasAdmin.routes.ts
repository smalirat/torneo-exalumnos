import { Response, Router } from 'express';
import {
  NombreCategoria,
  NombreTorneo,
  RolUsuario,
} from '@prisma/client';
import { z, ZodError } from 'zod';

import { prisma } from '../../lib/prisma';
import { requireRoleView } from '../../middleware/authView';
import { AppError } from '../../utils/AppError';

import {
  asyncViewHandler,
  manejarErrorVista,
} from './vistaErrorHandler';


import { crearPartidoSchema } from '../partidos/partidos.validation';
import { crearPartido } from '../partidos/partidos.service';

import { crearSancionSchema } from '../sanciones/sanciones.validation';
import { crearSancion, eliminarSancion } from '../sanciones/sanciones.service';

import { importarExcel } from '../excel/imports/excelImport.service';
import { upload } from '../excel/imports/excelImport.routes';

import { crearTemporadaSchema } from '../temporadas/temporadas.validation';
import { crearTorneoSchema } from '../torneos/torneos.validation';
import { crearCategoriaSchema } from '../categorias/categorias.validation';

import {
  crearCategoriaDesdeAdmin,
  crearTemporadaDesdeAdmin,
  crearTorneoDesdeAdmin,
  crearZonaDesdeAdmin,
  eliminarCategoriaDesdeAdmin,
  eliminarTemporadaDesdeAdmin,
  eliminarTorneoDesdeAdmin,
  eliminarZonaDesdeAdmin,
  obtenerEstructuraAdmin,
} from './estructuraAdmin.service';

import {
  crearEquipoEInscribirDesdeAdmin,
  eliminarEquipoDesdeAdmin,
  inscribirEquipoExistenteDesdeAdmin,
  obtenerGestionEquiposAdmin,
} from './equiposAdmin.service';

import { eliminarInscripcion } from '../inscripciones/inscripciones.service';

import { adminOperativoRouter } from './adminOperativo.routes';

export const vistasAdminRouter = Router();

const crearZonaAdminSchema = z.object({
  categoriaId: z.coerce.number().int().positive(),
  numero: z.coerce.number().int().positive(),
});

const destinoEquipoAdminSchema = z.string().regex(
    /^(categoria|zona):\d+$/,
    'Destino inválido.',
  );

const inscribirEquipoExistenteAdminSchema = z.object({
    equipoId:
      z.coerce
        .number()
        .int()
        .positive(),

    destino:
      destinoEquipoAdminSchema,
  });

const crearEquipoEInscribirAdminSchema = z.object({
    nombre:
      z.string()
        .trim()
        .min(
          1,
          'El nombre del equipo es obligatorio.',
        )
        .max(
          100,
          'El nombre del equipo no puede superar los 100 caracteres.',
        ),

    destino:
      destinoEquipoAdminSchema,
  });

// Todo lo que sigue requiere estar logueado como ADMIN.
vistasAdminRouter.use(requireRoleView(RolUsuario.ADMIN));

vistasAdminRouter.get('/', (req, res) => {
  res.render('admin/dashboard', {
    titulo: 'Admin',
    ok: req.query.ok,
    error: req.query.error,
  });
});

// ============================================================
// ESTRUCTURA
// Temporada -> Torneo -> Categoria -> Zona
// ============================================================

function mensajeErrorFormulario(err: unknown): string {
  if (err instanceof ZodError) {
    return err.issues
      .map((issue) => issue.message)
      .join(' ');
  }

  if (err instanceof Error) {
    return err.message;
  }

  return 'No se pudo guardar el cambio.';
}

function statusErrorFormulario(err: unknown): number {
  if (err instanceof AppError) {
    return err.statusCode;
  }

  return 400;
}

async function renderEstructura(
  res: Response,
  extra: {
    ok?: unknown;
    error?: unknown;
    status?: number;
  } = {},
) {
  const { temporadas, problemas } =
    await obtenerEstructuraAdmin();

  res
    .status(extra.status ?? 200)
    .render('admin/estructura', {
      titulo: 'Estructura del torneo',
      temporadas,
      problemas,
      nombresTorneo: Object.values(NombreTorneo),
      nombresCategoria: Object.values(NombreCategoria),
      ok: extra.ok,
      error: extra.error,
    });
}

// ---------- pantalla ----------

vistasAdminRouter.get(
  '/estructura',
  asyncViewHandler(async (req, res) => {
    await renderEstructura(res, {
      ok: req.query.ok,
      error: req.query.error,
    });
  }),
);

// ---------- temporada ----------

vistasAdminRouter.post(
  '/estructura/temporadas',
  asyncViewHandler(async (req, res) => {
    try {
      const input = crearTemporadaSchema.parse(req.body);

      await crearTemporadaDesdeAdmin(input);

      res.redirect(
        '/panel/admin/estructura?ok=' +
          encodeURIComponent(
            `Temporada ${input.anio} creada.`,
          ),
      );
    } catch (err) {
      await renderEstructura(res, {
        error: mensajeErrorFormulario(err),
        status: statusErrorFormulario(err),
      });
    }
  }),
);

// ---------- torneo ----------

vistasAdminRouter.post(
  '/estructura/torneos',
  asyncViewHandler(async (req, res) => {
    try {
      const input = crearTorneoSchema.parse(req.body);

      await crearTorneoDesdeAdmin(input);

      res.redirect(
        '/panel/admin/estructura?ok=' +
          encodeURIComponent(
            `Torneo ${input.nombre} creado.`,
          ),
      );
    } catch (err) {
      await renderEstructura(res, {
        error: mensajeErrorFormulario(err),
        status: statusErrorFormulario(err),
      });
    }
  }),
);

// ---------- categoría ----------

vistasAdminRouter.post(
  '/estructura/categorias',
  asyncViewHandler(async (req, res) => {
    try {
      const input = crearCategoriaSchema.parse(req.body);

      await crearCategoriaDesdeAdmin(input);

      res.redirect(
        '/panel/admin/estructura?ok=' +
          encodeURIComponent(
            `Categoría ${input.nombre} creada.`,
          ),
      );
    } catch (err) {
      await renderEstructura(res, {
        error: mensajeErrorFormulario(err),
        status: statusErrorFormulario(err),
      });
    }
  }),
);

// ---------- zona ----------

vistasAdminRouter.post(
  '/estructura/zonas',
  asyncViewHandler(async (req, res) => {
    try {
      const input =
        crearZonaAdminSchema.parse(req.body);

      const zona = await crearZonaDesdeAdmin({
        categoriaId: input.categoriaId,
        nombre: `Zona ${input.numero}`,
      });

      res.redirect(
        '/panel/admin/estructura?ok=' +
          encodeURIComponent(
            `${zona.nombre} creada.`,
          ),
      );
    } catch (err) {
      await renderEstructura(res, {
        error: mensajeErrorFormulario(err),
        status: statusErrorFormulario(err),
      });
    }
  }),
);

// ============================================================
// EQUIPOS E INSCRIPCIONES
// ============================================================

async function renderEquiposAdmin(
  res: Response,
  extra: {
    ok?: unknown;
    error?: unknown;
    status?: number;
  } = {},
) {
  const {
    equipos,
    destinos,
    inscripciones,
  } =
    await obtenerGestionEquiposAdmin();

  res
    .status(extra.status ?? 200)
    .render(
      'admin/equipos',
      {
        titulo:
          'Equipos e inscripciones',

        equipos,
        destinos,
        inscripciones,

        ok:
          extra.ok,

        error:
          extra.error,
      },
    );
}


// ------------------------------------------------------------
// Pantalla
// ------------------------------------------------------------

vistasAdminRouter.get(
  '/equipos',

  asyncViewHandler(
    async (req, res) => {
      await renderEquiposAdmin(
        res,
        {
          ok:
            req.query.ok,

          error:
            req.query.error,
        },
      );
    },
  ),
);


// ------------------------------------------------------------
// Inscribir equipo existente
// ------------------------------------------------------------

vistasAdminRouter.post(
  '/equipos/inscribir',

  asyncViewHandler(
    async (req, res) => {
      try {
        const input =
          inscribirEquipoExistenteAdminSchema.parse(
            req.body,
          );

        await inscribirEquipoExistenteDesdeAdmin(
          input,
        );

        res.redirect(
          '/panel/admin/equipos?ok=' +
            encodeURIComponent(
              'Equipo inscripto correctamente.',
            ),
        );
      } catch (err) {
        await renderEquiposAdmin(
          res,
          {
            error:
              mensajeErrorFormulario(
                err,
              ),

            status:
              statusErrorFormulario(
                err,
              ),
          },
        );
      }
    },
  ),
);


// ------------------------------------------------------------
// Crear equipo nuevo + inscribir
// ------------------------------------------------------------

vistasAdminRouter.post(
  '/equipos/crear',

  asyncViewHandler(
    async (req, res) => {
      try {
        const input =
          crearEquipoEInscribirAdminSchema.parse(
            req.body,
          );

        const resultado =
          await crearEquipoEInscribirDesdeAdmin(
            input,
          );

        res.redirect(
          '/panel/admin/equipos?ok=' +
            encodeURIComponent(
              `Equipo "${resultado.equipo.nombre}" creado e inscripto correctamente.`,
            ),
        );
      } catch (err) {
        await renderEquiposAdmin(
          res,
          {
            error:
              mensajeErrorFormulario(
                err,
              ),

            status:
              statusErrorFormulario(
                err,
              ),
          },
        );
      }
    },
  ),
);

// ============================================================
// CARGAR RESULTADO DE PARTIDO
// ============================================================

async function categoriasParaSelect() {
  return prisma.categoria.findMany({
    include: {
      torneo: {
        include: {
          temporada: true,
        },
      },
    },
    orderBy: [
      {
        torneo: {
          temporada: {
            anio: 'desc',
          },
        },
      },
    ],
  });
}

vistasAdminRouter.get(
  '/partidos/nuevo',
  asyncViewHandler(async (req, res) => {
    const categorias =
      await categoriasParaSelect();

    res.render('admin/partido-nuevo', {
      titulo: 'Cargar partido',
      categorias,
      ok: req.query.ok,
      error: req.query.error,
    });
  }),
);

vistasAdminRouter.post(
  '/partidos',
  asyncViewHandler(async (req, res) => {
    try {
      const body = { ...req.body };

      for (const campo of [
        'zonaId',
        'golesLocal',
        'golesVisitante',
        'campo',
        'cancha',
        'horario',
      ]) {
        if (body[campo] === '') {
          delete body[campo];
        }
      }

      const input =
        crearPartidoSchema.parse(body);

      await crearPartido(input);

      res.redirect(
        '/panel/admin/partidos/nuevo?ok=' +
          encodeURIComponent(
            'Partido guardado.',
          ),
      );
    } catch (err) {
      const categorias =
        await categoriasParaSelect();

      if (err instanceof Error) {
        res.status(400).render(
          'admin/partido-nuevo',
          {
            titulo: 'Cargar partido',
            categorias,
            error: err.message,
          },
        );

        return;
      }

      manejarErrorVista(err, res);
    }
  }),
);

// ============================================================
// IMPORTAR EXCEL
// ============================================================

async function torneosParaSelect() {
  return prisma.torneo.findMany({
    include: {
      temporada: true,
    },
    orderBy: [
      {
        temporada: {
          anio: 'desc',
        },
      },
      {
        nombre: 'asc',
      },
    ],
  });
}

vistasAdminRouter.get(
  '/importar-excel',
  asyncViewHandler(async (_req, res) => {
    const torneos =
      await torneosParaSelect();

    res.render('admin/importar-excel', {
      titulo: 'Importar Excel',
      torneos,
    });
  }),
);

vistasAdminRouter.post(
  '/importar-excel',
  upload.single('archivo'),
  asyncViewHandler(async (req, res) => {
    const torneos =
      await torneosParaSelect();

    const torneoId =
      Number(req.body.torneoId);

    if (!req.file) {
      res.status(400).render(
        'admin/importar-excel',
        {
          titulo: 'Importar Excel',
          torneos,
          error: 'Falta el archivo.',
        },
      );

      return;
    }

    try {
      const fs = await import('fs');

      const resumen = await importarExcel(
        torneoId,
        {
          buffer:
            req.file.buffer ??
            fs.readFileSync(req.file.path),

          nombreOriginal:
            req.file.originalname,

          rutaGuardada:
            req.file.path,
        },
      );

      res.render('admin/importar-excel', {
        titulo: 'Importar Excel',
        torneos,
        resumen,
      });
    } catch (err) {
      if (err instanceof Error) {
        res.status(400).render(
          'admin/importar-excel',
          {
            titulo: 'Importar Excel',
            torneos,
            error: err.message,
          },
        );

        return;
      }

      manejarErrorVista(err, res);
    }
  }),
);

// ============================================================
// CARGAR SANCIÓN
// ============================================================

vistasAdminRouter.get(
  '/sanciones/nueva',
  asyncViewHandler(async (_req, res) => {
    const [torneos, sanciones] = await Promise.all([
      torneosParaSelect(),
      prisma.sancion.findMany({
        include: {
          jugador: true,
          equipo: true,
          torneo: { include: { temporada: true } },
        },
        orderBy: [{ torneo: { temporada: { anio: 'desc' } } }, { createdAt: 'desc' }],
        take: 50,
      }),
    ]);

    res.render('admin/sancion-nueva', {
      titulo: 'Cargar sanción',
      torneos,
      sanciones,
      ok: _req.query.ok,
      error: _req.query.error,
    });
  }),
);

vistasAdminRouter.post(
  '/sanciones/:id/eliminar',
  asyncViewHandler(async (req, res) => {
    try {
      await eliminarSancion(Number(req.params.id));
      redirectConMensaje(res, '/panel/admin/sanciones/nueva', 'Sanción eliminada.');
    } catch (err) {
      redirectConMensaje(res, '/panel/admin/sanciones/nueva', '', err);
    }
  }),
);

vistasAdminRouter.post(
  '/sanciones',
  asyncViewHandler(async (req, res) => {
    try {
      const input =
        crearSancionSchema.parse(req.body);

      await crearSancion(input);

      res.redirect(
        '/panel/admin?ok=' +
          encodeURIComponent(
            'Sanción cargada.',
          ),
      );
    } catch (err) {
      const torneos =
        await torneosParaSelect();

      if (err instanceof Error) {
        res.status(400).render(
          'admin/sancion-nueva',
          {
            titulo: 'Cargar sanción',
            torneos,
            error: err.message,
          },
        );

        return;
      }

      manejarErrorVista(err, res);
    }
  }),
);

// ============================================================
// BAJAS DE ESTRUCTURA (con protección de datos dependientes)
// ============================================================

function redirectConMensaje(res: Response, urlBase: string, ok: string, err?: unknown) {
  if (err) {
    res.redirect(
      urlBase +
        '?error=' +
        encodeURIComponent(mensajeErrorFormulario(err)),
    );
    return;
  }

  res.redirect(
    urlBase +
      '?ok=' +
      encodeURIComponent(ok),
  );
}

vistasAdminRouter.post(
  '/estructura/temporadas/:id/eliminar',
  asyncViewHandler(async (req, res) => {
    try {
      await eliminarTemporadaDesdeAdmin(Number(req.params.id));
      redirectConMensaje(res, '/panel/admin/estructura', 'Temporada eliminada.');
    } catch (err) {
      redirectConMensaje(res, '/panel/admin/estructura', '', err);
    }
  }),
);

vistasAdminRouter.post(
  '/estructura/torneos/:id/eliminar',
  asyncViewHandler(async (req, res) => {
    try {
      await eliminarTorneoDesdeAdmin(Number(req.params.id));
      redirectConMensaje(res, '/panel/admin/estructura', 'Torneo eliminado.');
    } catch (err) {
      redirectConMensaje(res, '/panel/admin/estructura', '', err);
    }
  }),
);

vistasAdminRouter.post(
  '/estructura/categorias/:id/eliminar',
  asyncViewHandler(async (req, res) => {
    try {
      await eliminarCategoriaDesdeAdmin(Number(req.params.id));
      redirectConMensaje(res, '/panel/admin/estructura', 'Categoría eliminada.');
    } catch (err) {
      redirectConMensaje(res, '/panel/admin/estructura', '', err);
    }
  }),
);

vistasAdminRouter.post(
  '/estructura/zonas/:id/eliminar',
  asyncViewHandler(async (req, res) => {
    try {
      await eliminarZonaDesdeAdmin(Number(req.params.id));
      redirectConMensaje(res, '/panel/admin/estructura', 'Zona eliminada.');
    } catch (err) {
      redirectConMensaje(res, '/panel/admin/estructura', '', err);
    }
  }),
);

vistasAdminRouter.post(
  '/equipos/:id/eliminar',
  asyncViewHandler(async (req, res) => {
    try {
      await eliminarEquipoDesdeAdmin(Number(req.params.id));
      redirectConMensaje(res, '/panel/admin/equipos', 'Equipo eliminado.');
    } catch (err) {
      redirectConMensaje(res, '/panel/admin/equipos', '', err);
    }
  }),
);

vistasAdminRouter.post(
  '/equipos/inscripciones/:id/eliminar',
  asyncViewHandler(async (req, res) => {
    try {
      await eliminarInscripcion(Number(req.params.id));
      redirectConMensaje(res, '/panel/admin/equipos', 'Inscripción eliminada.');
    } catch (err) {
      redirectConMensaje(res, '/panel/admin/equipos', '', err);
    }
  }),
);

// Pantallas operativas (partidos, jugadores, disciplina, campeones, eventos).
vistasAdminRouter.use(adminOperativoRouter);