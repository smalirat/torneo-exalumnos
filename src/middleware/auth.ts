import { NextFunction, Request, Response } from 'express';
import { ForbiddenError, UnauthorizedError } from '../utils/AppError';
import { RolUsuario } from '@prisma/client';

// La mayoría de los GET son públicos (standings, goleadores, etc — ver spec).
// Solo se usa requireAuth/requireRole en las rutas de escritura (admin) y en
// la ruta de plantel que el delegado puede ver.
export function requireAuth(req: Request, _res: Response, next: NextFunction) {
  if (!req.session.userId) {
    throw new UnauthorizedError();
  }
  next();
}

export function requireRole(...roles: RolUsuario[]) {
  return (req: Request, _res: Response, next: NextFunction) => {
    if (!req.session.userId) {
      throw new UnauthorizedError();
    }
    if (!req.session.rol || !roles.includes(req.session.rol)) {
      throw new ForbiddenError();
    }
    next();
  };
}
