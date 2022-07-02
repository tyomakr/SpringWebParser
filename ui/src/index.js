import React from 'react';
import {createRoot} from 'react-dom/client'
import App from './App';
import { Provider } from "mobx-react";
import mainStore from "./store/mainStore";
import storeFI from "./store/storeFI";
import 'bootstrap/dist/css/bootstrap.min.css';
import 'react-toastify/dist/ReactToastify.css';
import './common/index.css';

const stores = {mainStore, storeFI}

const container = document.getElementById('root');
const root = createRoot(container);

root.render(
    <Provider {...stores}>
        <App />
    </Provider>
);
