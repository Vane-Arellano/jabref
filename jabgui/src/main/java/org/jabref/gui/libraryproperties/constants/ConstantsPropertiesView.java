package org.jabref.gui.libraryproperties.constants;

import java.util.Optional;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.util.converter.DefaultStringConverter;

import org.jabref.gui.DialogService;
import org.jabref.gui.icon.IconTheme;
import org.jabref.gui.libraryproperties.AbstractPropertiesTabView;
import org.jabref.gui.libraryproperties.PropertiesTab;
import org.jabref.gui.preferences.GuiPreferences;
import org.jabref.gui.util.ValueTableCellFactory;
import org.jabref.gui.util.ViewModelTextFieldTableCellVisualizationFactory;
import org.jabref.logic.l10n.Localization;
import org.jabref.model.database.BibDatabaseContext;

import com.airhacks.afterburner.views.ViewLoader;
import jakarta.inject.Inject;

public class ConstantsPropertiesView extends AbstractPropertiesTabView<ConstantsPropertiesViewModel> implements PropertiesTab {

    @FXML private TableView<ConstantsItemModel> stringsList;
    @FXML private TableColumn<ConstantsItemModel, String> labelColumn;
    @FXML private TableColumn<ConstantsItemModel, String> contentColumn;
    @FXML private TableColumn<ConstantsItemModel, String> actionsColumn;
    @FXML private Button addStringButton;

    @Inject private GuiPreferences preferences;
    @Inject private DialogService dialogService;

    public ConstantsPropertiesView(BibDatabaseContext databaseContext) {
        this.databaseContext = databaseContext;

        ViewLoader.view(this)
                  .root(this)
                  .load();
    }

    @Override
    public String getTabName() {
        return Localization.lang("String constants");
    }

    public void initialize() {
        this.viewModel = new ConstantsPropertiesViewModel(databaseContext, dialogService, preferences.getExternalApplicationsPreferences());

        addStringButton.setTooltip(new Tooltip(Localization.lang("New string")));

        labelColumn.setSortable(true);
        labelColumn.setReorderable(false);

        labelColumn.setCellValueFactory(cellData -> cellData.getValue().labelProperty());
        new ViewModelTextFieldTableCellVisualizationFactory<ConstantsItemModel, String>()
                .withValidation(ConstantsItemModel::labelValidation)
                .install(labelColumn, new DefaultStringConverter());
        labelColumn.setOnEditCommit((TableColumn.CellEditEvent<ConstantsItemModel, String> cellEvent) -> {

            TableView<ConstantsItemModel> tableView = cellEvent.getTableView();
            ConstantsItemModel cellItem = tableView.getItems()
                                                   .get(cellEvent.getTablePosition().getRow());

            Optional<ConstantsItemModel> existingItem = viewModel.labelAlreadyExists(cellEvent.getNewValue());

            if (existingItem.isPresent() && !existingItem.get().equals(cellItem)) {
                dialogService.showErrorDialogAndWait(Localization.lang(
                        "A string with the label '%0' already exists.",
                        cellEvent.getNewValue()));
                cellItem.setLabel(cellEvent.getOldValue());
            } else {
                cellItem.setLabel(cellEvent.getNewValue());
            }

            // Resort the entries based on the keys and set the focus to the newly-created entry
            viewModel.resortStrings();
            TableView.TableViewSelectionModel<ConstantsItemModel> selectionModel = tableView.getSelectionModel();
            selectionModel.select(cellItem);
            selectionModel.focus(selectionModel.getSelectedIndex());
            tableView.refresh();
            tableView.scrollTo(cellItem);
        });

        contentColumn.setSortable(true);
        contentColumn.setReorderable(false);
        contentColumn.setCellValueFactory(cellData -> cellData.getValue().contentProperty());
        new ViewModelTextFieldTableCellVisualizationFactory<ConstantsItemModel, String>()
                .withValidation(ConstantsItemModel::contentValidation)
                .install(contentColumn, new DefaultStringConverter());
        contentColumn.setOnEditCommit((TableColumn.CellEditEvent<ConstantsItemModel, String> cell) ->
                cell.getRowValue().setContent(cell.getNewValue()));

        actionsColumn.setSortable(false);
        actionsColumn.setReorderable(false);
        actionsColumn.setCellValueFactory(cellData -> cellData.getValue().labelProperty());
        new ValueTableCellFactory<ConstantsItemModel, String>()
                .withGraphic(_ -> IconTheme.JabRefIcons.DELETE_ENTRY.getGraphicNode())
                .withTooltip(label -> Localization.lang("Remove string %0", label))
                .withOnMouseClickedEvent(_ -> _ ->
                        viewModel.removeString(stringsList.getFocusModel().getFocusedItem()))
                .install(actionsColumn);

        stringsList.itemsProperty().bindBidirectional(viewModel.stringsListProperty());
        stringsList.setEditable(true);
    }

    @FXML
    private void addString() {
        viewModel.addNewString();
        stringsList.edit(stringsList.getItems().size() - 1, labelColumn);
    }

    @FXML
    private void openHelp() {
        viewModel.openHelpPage();
    }
}
