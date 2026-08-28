import { Router } from 'express';
import { login } from '../auth/auth.service';
import { UnauthorizedError } from '../../utils/AppError';
import { asyncViewHandler } from './vistaErrorHandler';

export const vistasAuthRouter = Router();

vistasAuthRouter.get('/login', (req, res) => {
  if (req.session.userId) {
    res.redirect('/panel/admin');
    return;
  }
  res.render('auth/login', { titulo: 'Ingresar', next: req.query.next ?? '' });
});

vistasAuthRouter.post(
  '/login',
  asyncViewHandler(async (req, res) => {
    const { username, password, next } = req.body as { username?: string; password?: string; next?: string };

    try {
      const usuario = await login({ username: username ?? '', password: password ?? '' });
      req.session.regenerate((err) => {
        if (err) throw err;
        req.session.userId = usuario.id;
        req.session.username = usuario.username;
        req.session.rol = usuario.rol;
        req.session.equipoId = usuario.equipoId;
        res.redirect(next && next.startsWith('/') ? next : '/panel/admin');
      });
    } catch (err) {
      if (err instanceof UnauthorizedError) {
        res.status(401).render('auth/login', { titulo: 'Ingresar', next: next ?? '', error: err.message });
        return;
      }
      throw err;
    }
  }),
);

vistasAuthRouter.post('/logout', (req, res) => {
  req.session.destroy(() => {
    res.clearCookie('connect.sid');
    res.redirect('/panel');
  });
});
