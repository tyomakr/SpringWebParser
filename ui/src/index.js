import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import { Provider } from 'mobx-react';
import StoreFI from './store/StoreFI';
import themeStore from './store/themeStore';
import 'react-toastify/dist/ReactToastify.css';

const stores = {
    storeFI: StoreFI,
    themeStore,
};

const container = document.getElementById('root');
const root = createRoot(container);

root.render(
    <Provider {...stores}>
        <App />
    </Provider>
);
