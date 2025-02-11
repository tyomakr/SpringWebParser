import React from 'react';
import {Link, Route, BrowserRouter, Routes} from 'react-router-dom';
import MainPage from './components/pages/MainPage';
import history from "./components/history";
import {inject, observer} from "mobx-react";
import './common/App.css';
import {ToastContainer} from "react-toastify";
import FWIStepper from "./components/FWIStepper";
import themeStore from "./store/themeStore";

const App = inject("mainStore", "storeFI", "themeStore")(observer(() => {
    return (
        <div className={themeStore?.mode}>
            <ToastContainer/>
            <BrowserRouter history={history}>

                <nav className="navbar navbar-expand navbar-dark bg-dark">
                    <Link to={"/"} className="navbar-brand">Images & Media Parser</Link>
                    <div className="navbar-nav mr-auto">

                        <li className="nav-item">
                            <Link to={"/"} className="nav-link">Главная</Link>
                        </li>
                        <li className="nav-item">
                            <Link to={"/fwi-stepper"} className="nav-link">Изображения</Link>
                        </li>
                        <li className="nav-item position-absolute end-0 mx-3">
                            <button className="btn btn-outline-light" type="button"
                                    onClick={themeStore.toggleMode}>Светлая/Темная тема
                            </button>
                        </li>
                    </div>
                </nav>

                <Routes>
                    <Route exact path="/" element={<MainPage/>}></Route>
                    <Route exact path="/fwi-stepper" element={<FWIStepper/>}></Route>
                </Routes>
            </BrowserRouter>
        </div>
    );
}));

export default App;