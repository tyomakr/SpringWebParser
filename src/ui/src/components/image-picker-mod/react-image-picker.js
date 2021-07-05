import React, { Component } from 'react'
import PropTypes from 'prop-types'
import { Map } from 'immutable'
import '../../common/image-picker.css'
import Image from './image'
import {inject, observer} from "mobx-react";

@inject('storeFI')
@observer
class ImagePicker extends Component {
    constructor(props) {
        super(props)

        this.state = {
            newerPickedImage: this.props.storeFI.newerPickedImage,
            pickedImageToArray: this.props.storeFI.pickedImageToArray,
            picked: Map()
        }
        this.handleImageClick = this.handleImageClick.bind(this)
        this.renderImage = this.renderImage.bind(this)
    }


    handleImageClick(image) {
        const { multiple, onPick } = this.props
        console.log("props on hic " + this.props)
        const pickedImage = multiple ? this.state.picked : Map()
        this.newerPickedImage =
            pickedImage.has(image.value) ?
                pickedImage.delete(image.value) :
                pickedImage.set(image.value, image.src)
         console.log("newerPicked - " + this.newerPickedImage)

        this.setState({picked: this.newerPickedImage})

        this.pickedImageToArray = []
        this.newerPickedImage.map((image, i) => this.pickedImageToArray.push({src: image, value: i}))
        console.log("pickedImageToArray size - " + this.pickedImageToArray.length)

        onPick(multiple ? this.pickedImageToArray : this.pickedImageToArray[0])
    }

    renderImage(image, i) {
        return (
            <Image
                src={image.src}
                isSelected={this.state.picked.has(image.value)}
                onImageClick={() => this.handleImageClick(image)}
                key={i}
            />
        )
    }

    render() {
        const { images } = this.props
        return (
            <div className="image_picker">
                { images.map(this.renderImage) }
                <div className="clear"/>
            </div>
        )
    }
}

ImagePicker.propTypes = {
    images: PropTypes.array,
    multiple: PropTypes.bool,
    onPick: PropTypes.func
}

export default ImagePicker