import { NextFunction, Request, Response } from 'express';
import { AppError } from '../../utils/AppError';

export function manejarErrorVista(err: unknown, res: Response) {
  if (err instanceof AppError) {
    res.status(err.statusCode).render('error', { titulo: 'Error', mensaje: err.message });
    return;
  }
  console.error(err);
  res.status(500).render('error', { titulo: 'Error', mensaje: 'Ocurrió un error inesperado.' });
}

type ViewHandler = (req: Request, res: Response, next: NextFunction) => Promise<unknown>;

// Como asyncHandler, pero en vez de pasarle el error al errorHandler global
// (que responde JSON), renderiza una página de error legible.
export function asyncViewHandler(fn: ViewHandler) {
  return async (req: Request, res: Response, next: NextFunction) => {
    try {
      await fn(req, res, next);
    } catch (err) {
      manejarErrorVista(err, res);
    }
  };
}
