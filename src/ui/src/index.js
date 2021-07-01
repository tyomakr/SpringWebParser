import React from 'react';
import ReactDOM from 'react-dom';
import App from './App';
import { Provider } from "mobx-react";
import mainStore from "./store/mainStore";
import storeFI from "./store/storeFI"
import 'bootstrap/dist/css/bootstrap.min.css';
import './common/index.css';

const stores = {mainStore, storeFI}

ReactDOM.render(
        <Provider {...stores}>
            <App />
        </Provider>,
    document.getElementById('root')
);
