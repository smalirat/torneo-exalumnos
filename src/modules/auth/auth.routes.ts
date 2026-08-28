import { Router } from 'express';
import { asyncHandler } from '../../middleware/asyncHandler';
import { loginSchema } from './auth.validation';
import { login } from './auth.service';

export const authRouter = Router();

authRouter.post(
  '/login',
  asyncHandler(async (req, res) => {
    const input = loginSchema.parse(req.body);
    const usuario = await login(input);

    // regenerate() evita session fixation: la cookie que tenía el browser
    // ANTES de loguearse (si tenía alguna) queda inválida.
    req.session.regenerate((err) => {
      if (err) throw err;
      req.session.userId = usuario.id;
      req.session.rol = usuario.rol;
      req.session.equipoId = usuario.equipoId;
      res.json({ id: usuario.id, username: usuario.username, rol: usuario.rol, equipoId: usuario.equipoId });
    });
  }),
);

authRouter.post(
  '/logout',
  asyncHandler(async (req, res) => {
    req.session.destroy((err) => {
      if (err) throw err;
      res.clearCookie('connect.sid');
      res.status(204).send();
    });
  }),
);

// No requiere estar logueado: es la ruta que el frontend consulta para
// saber "¿hay una sesión activa?" al cargar la página.
authRouter.get('/me', (req, res) => {
  if (!req.session.userId) {
    res.json({ authenticated: false });
    return;
  }
  res.json({
    authenticated: true,
    userId: req.session.userId,
    rol: req.session.rol,
    equipoId: req.session.equipoId,
  });
});
