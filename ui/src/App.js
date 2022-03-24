import React, {Component} from 'react';
import {Link, Route, Router, Switch} from 'react-router-dom';
import MainPage from './components/pages/MainPage';
import FWIStepper from "./components/FWIStepper";
import history from "./components/history";
import {inject, observer} from "mobx-react";
import './common/App.css';
import {ToastContainer} from "react-toastify";

const App = inject("mainStore", "storeFI")(observer(() => {
    return (
        <div>
            <ToastContainer/>
            <Router history={history}>

                <nav className="navbar navbar-expand navbar-dark bg-dark">
                    <Link to={"/"} className="navbar-brand">Images & Media Parser</Link>
                    <div className="navbar-nav mr-auto">
                        <li className="nav-item">
                            <Link to={"/"} className="nav-link">Главная</Link>
                        </li>
                        <li className="nav-item">
                            <Link to={"/fwi-stepper"} className="nav-link">Изображения</Link>
                        </li>
                    </div>
                </nav>

                <Switch>
                    <Route exact path="/" component={MainPage}/>
                    <Route exact path="/fwi-stepper" component={FWIStepper}/>
                </Switch>
            </Router>
        </div>
    );
}));

export default App;