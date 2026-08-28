import request from 'supertest';
import { createApp } from '../src/app';

describe('GET /health', () => {
  it('responde 200 con status ok', async () => {
    const app = createApp();
    const res = await request(app).get('/health');
    expect(res.status).toBe(200);
    expect(res.body).toEqual({ status: 'ok' });
  });

  it('devuelve 404 con formato de error consistente para rutas inexistentes', async () => {
    const app = createApp();
    const res = await request(app).get('/esto-no-existe');
    expect(res.status).toBe(404);
    expect(res.body.error.code).toBe('NOT_FOUND');
  });
});
