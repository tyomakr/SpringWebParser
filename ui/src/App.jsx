import React from 'react';

export default function App() {
  return (
    <main className="app">
      <section className="hero">
        <p className="eyebrow">AK Content Pipeline</p>
        <h1>Минимальный UI‑скелет для AKCP</h1>
        <p className="lead">
          Этот интерфейс — заглушка для будущего фронтенда. Сейчас фокус на backend‑MVP.
        </p>
        <div className="card">
          <div>
            <h2>Backend</h2>
            <p>Swagger: <code>/swagger-ui.html</code></p>
          </div>
          <div>
            <h2>Статус</h2>
            <p>Запуск через Docker Compose</p>
          </div>
        </div>
      </section>
    </main>
  );
}
