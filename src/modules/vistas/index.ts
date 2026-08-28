import { Router } from 'express';
import { vistasRouter } from './vistas.routes';
import { vistasAuthRouter } from './vistasAuth.routes';
import { vistasAdminRouter } from './vistasAdmin.routes';

export const panelRouter = Router();

// Disponible en TODAS las vistas bajo /panel (layout.ejs lo usa para el nav).
panelRouter.use((req, res, next) => {
  res.locals.usuario = req.session.userId
    ? { username: req.session.username, rol: req.session.rol }
    : null;
  next();
});

panelRouter.use(vistasAuthRouter); // /panel/login, /panel/logout
panelRouter.use('/admin', vistasAdminRouter); // /panel/admin/*
panelRouter.use(vistasRouter); // /panel, /panel/standings, /panel/partidos, etc.
