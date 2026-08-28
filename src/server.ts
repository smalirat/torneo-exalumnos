import { createApp } from '../app';
import { env } from './config/env';

const app = createApp();

app.listen(env.PORT, () => {
  console.log(`🚀 Server escuchando en http://localhost:${env.PORT}`);
});
