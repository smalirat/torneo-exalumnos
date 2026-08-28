import express from 'express';
import session from 'express-session';
import connectPgSimple from 'connect-pg-simple';
import { Pool } from 'pg';
import path from 'path';
import expressLayouts from 'express-ejs-layouts';
import { env } from './config/env';
import { errorHandler } from './middleware/errorHandler';
import { router as apiRouter } from './modules/router';
import { panelRouter } from './modules/vistas';

const PgSession = connectPgSimple(session);

export function createApp() {
  const app = express();

  app.use(express.json());
  app.use(express.urlencoded({ extended: true })); // necesario para los <form> del panel HTML

  app.set('view engine', 'ejs');
  app.set('views', path.join(__dirname, 'views'));
  app.use(expressLayouts);
  app.set('layout', 'layout');
  app.use('/public', express.static(path.join(__dirname, '..', 'public')));

  // Store de sesiones en Postgres (misma DB que el resto de los datos,
  // así no dependemos de Redis para este alcance). La tabla "session"
  // la crea automáticamente connect-pg-simple si createTableIfMissing=true.
  const sessionPool = new Pool({ connectionString: env.DATABASE_URL });

  app.use(
    session({
      store: new PgSession({ pool: sessionPool, createTableIfMissing: true }),
      secret: env.SESSION_SECRET,
      resave: false,
      saveUninitialized: false,
      cookie: {
        httpOnly: true,
        secure: env.NODE_ENV === 'production',
        maxAge: 1000 * 60 * 60 * 8, // 8 horas
      },
    }),
  );

  app.get('/health', (_req, res) => res.json({ status: 'ok' }));

  app.use('/panel', panelRouter);
  app.use('/', apiRouter);

  // 404 para rutas no encontradas (debe ir después de montar todas las rutas)
  app.use((req, res) => {
    res.status(404).json({ error: { code: 'NOT_FOUND', message: `Ruta ${req.method} ${req.path} no existe` } });
  });

  // El error handler SIEMPRE va al final, con los 4 parámetros
  // (Express lo detecta como error middleware por la arity de la función).
  app.use(errorHandler);

  return app;
}
