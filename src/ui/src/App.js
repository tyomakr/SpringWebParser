import React, {Component} from 'react';
import {Link, Route, Router, Switch} from 'react-router-dom';
import MainPage from './components/pages/MainPage';
import FWIStepper from "./components/FWIStepper";
import history from "./components/history";
import {inject, observer} from "mobx-react";
import './common/App.css';
import Gallery from "./components/pages/Gallery";



@inject("mainStore", "storeFI")
@observer
class App extends Component {

    render() {
        return (
            <div>
                <Router history={history}>

                    <nav className="navbar navbar-expand navbar-dark bg-dark">
                        <Link to={"/"} className="navbar-brand">Images & Media Parser</Link>
                        <div className="navbar-nav mr-auto">
                            <li className="nav-item">
                                <Link to={"/"} className="nav-link">Главная</Link>
                            </li>
                            <li className="nav-item">
                                <Link to={"/fwi-stepper"} className="nav-link">Степпер</Link>
                            </li>
                            <li className="nav-item">
                                <Link to={"/gallery"} className="nav-link">Галерея</Link>
                            </li>
                        </div>
                    </nav>


                    <Switch>
                        <Route exact path="/" component={MainPage}/>
                        <Route exact path="/gallery" component={Gallery}/>
                        <Route exact path="/fwi-stepper" component={FWIStepper} />
                    </Switch>
                </Router>
            </div>
        );
    }
}

export default App;

