import React, {useEffect} from "react";
import {Helmet} from "react-helmet/es/Helmet";
import Pagination from "../Pagination";
import {inject, observer} from "mobx-react";
import storeFI from "../../store/storeFI";
import ImagePicker from "react-image-picker"
import '../../common/image-picker.css'

@inject('storeFI')
@observer
class Gallery extends React.Component {

    constructor(props) {
        super(props);

        this.state = {
            items: this.props.storeFI.webImages,
            pageOfItems: [],
        };

        this.onChangePage = this.onChangePage.bind(this);
        this.onPick = this.onPick.bind(this);
        this.onHandleSelectImages = this.onHandleSelectImages.bind(this);
    }


    onChangePage(pageOfItems) {
        // update state with new page of items
        this.setState({ pageOfItems: pageOfItems });
        //this.setState(this.state.image = {})
    }

    //select images ImagesPicker
    onPick(image) {
        this.setState({ image });
    }

    onHandleSelectImages() {
        this.setState(this.baseState)


    }


    render() {

        return (
            <div>
                <Helmet
                    htmlAttributes={{"lang": "ru", "amp": undefined}}
                    title="Галерея и выбор изображений"
                    titleTemplate="Spring web parser - %s" />

                <div className="jumbotron">
                    <h3 className="header-section">Просмотр и выбор изображений</h3>
                    <div className="container-fluid">

                        <div className="text-center">
                            <ImagePicker
                                images={this.state.pageOfItems.map((webImage, i) => ({
                                    src: webImage.directLink,
                                    value: i
                            }))}
                            onPick={this.onPick}
                            multiple />
                            <hr/>

                            <button type="button" onClick={()=> this.onHandleSelectImages()}>OK</button>

                            {/*<button type="button" onClick={() => console.log(this.state.image)}>OK</button>*/}
                            <Pagination items={this.state.items} onChangePage={this.onChangePage}/>
                        </div>

                    </div>
                </div>
            </div>
        );
    }
}

export default Gallery