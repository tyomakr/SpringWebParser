import React from "react";
import {Helmet} from "react-helmet/es/Helmet";
import Pagination from "../Pagination";
import {inject, observer} from "mobx-react";
import storeFI from "../../store/storeFI";
import {ImageList} from "@mui/material";
import {ImageListItem} from "@mui/material";
import {ImageListItemBar} from "@mui/material";
import {IconButton} from "@mui/material";
import InfoIcon from '@mui/icons-material/Info';


@inject('storeFI')
@observer
class Gallery extends React.Component {

    constructor(props) {
        super(props);

        this.state = {
            items: this.props.storeFI.webImages,
            pageOfItems: []
        };

        this.onChangePage = this.onChangePage.bind(this);
        this.onHandleSelectImages = this.onHandleSelectImages.bind(this);
    }


    onChangePage(pageOfItems) {
        // update state with new page of items
        this.setState({ pageOfItems: pageOfItems });
    }

    //click button
    onHandleSelectImages(webImage) {
        storeFI.selectedImages.push(webImage)
        console.log(storeFI.selectedImages.length)
    }


    render() {
        return (
            <div>
                <Helmet
                    htmlAttributes={{"lang": "ru", "amp": undefined}}
                    title="Галерея и выбор изображений"
                    titleTemplate="Spring web parser - %s"/>

                <div className="jumbotron">
                    <h3 className="header-section">Просмотр и выбор изображений</h3>
                    <div className="container-fluid">
                        <div className="text-center">
                            <ImageList cols={5} rowHeight='auto'>
                                {this.state.pageOfItems.map((webImage) => (
                                    <ImageListItem key={webImage.id}
                                                   // sx={{width: 300, height: 300}}
                                    >
                                        <img
                                            className={"img-gal"}
                                            src={webImage.directLink}
                                            alt={webImage.id}
                                            loading="lazy"
                                        />
                                        <ImageListItemBar
                                            title={""}
                                            actionIcon={
                                                <IconButton type="button"
                                                            sx={{color: 'rgba(255, 255, 255, 0.54)'}}
                                                            onClick={() => this.onHandleSelectImages(webImage)}>
                                                    <InfoIcon/>
                                                </IconButton>
                                            }
                                        />
                                    </ImageListItem>
                                ))}
                            </ImageList>
                            <Pagination items={this.state.items} onChangePage={this.onChangePage}/>
                        </div>
                    </div>
                </div>
            </div>
        );
    }
}

export default Gallery;
