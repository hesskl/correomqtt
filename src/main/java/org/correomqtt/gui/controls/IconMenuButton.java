package org.correomqtt.gui.controls;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.MenuButton;

public class IconMenuButton extends MenuButton {

    private final StringProperty iconProperty = new SimpleStringProperty();

    public IconMenuButton(){
        super();
        iconProperty.addListener(this::iconChange);
    }

    private void iconChange(ObservableValue<? extends String> observable, String oldValue, String newValue) {
        ThemedFontIcon themedFontIcon = new ThemedFontIcon();
        themedFontIcon.setIconLiteral(newValue);
        themedFontIcon.setIconSize(18);
        this.setGraphic(themedFontIcon);
        //this.setPadding(new Insets(1,8,1,8));
        this.setHeight(28);
        this.setMinHeight(28);
    }

    public void setIcon(String icon){
        this.iconProperty.set(icon);
    }

    public String getIcon(){
       return this.iconProperty.get();
    }
}