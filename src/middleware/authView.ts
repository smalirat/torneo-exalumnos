import { NextFunction, Request, Response } from 'express';
import { RolUsuario } from '@prisma/client';

// A diferencia de src/middleware/auth.ts (que tira AppError -> JSON), acá
// redirigimos a /panel/login — porque este middleware protege páginas HTML,
// no endpoints de API. Guardamos la URL pedida en ?next= para volver ahí
// después de loguearse.
export function requireAuthView(req: Request, res: Response, next: NextFunction) {
  if (!req.session.userId) {
    res.redirect(`/panel/login?next=${encodeURIComponent(req.originalUrl)}`);
    return;
  }
  next();
}

export function requireRoleView(...roles: RolUsuario[]) {
  return (req: Request, res: Response, next: NextFunction) => {
    if (!req.session.userId) {
      res.redirect(`/panel/login?next=${encodeURIComponent(req.originalUrl)}`);
      return;
    }
    if (!req.session.rol || !roles.includes(req.session.rol)) {
      res.status(403).render('error', { titulo: 'Sin permiso', mensaje: 'No tenés permiso para ver esta página.' });
      return;
    }
    next();
  };
}
