package org.jabref.gui.errorconsole;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javafx.beans.property.ListProperty;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.collections.ObservableList;

import org.jabref.gui.gui.AbstractViewModel;
import org.jabref.gui.gui.ClipBoardManager;
import org.jabref.gui.gui.DialogService;
import org.jabref.gui.gui.MappedList;
import org.jabref.gui.desktop.JabRefDesktop;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.logging.LogMessages;
import org.jabref.logic.util.BuildInfo;
import org.jabref.logic.util.OS;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.client.utils.URIBuilder;
import org.apache.logging.log4j.core.LogEvent;

public class ErrorConsoleViewModel extends AbstractViewModel {

    private static final Log LOGGER = LogFactory.getLog(ErrorConsoleViewModel.class);
    private final DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
    private final Date date = new Date();
    private final DialogService dialogService;
    private final ClipBoardManager clipBoardManager;
    private final BuildInfo buildInfo;
    private final ListProperty<LogEventViewModel> allMessagesData;

    public ErrorConsoleViewModel(DialogService dialogService, ClipBoardManager clipBoardManager, BuildInfo buildInfo) {
        this.dialogService = Objects.requireNonNull(dialogService);
        this.clipBoardManager = Objects.requireNonNull(clipBoardManager);
        this.buildInfo = Objects.requireNonNull(buildInfo);

        ObservableList<LogEventViewModel> eventViewModels =
                new MappedList<>(LogMessages.getInstance().getMessages(), LogEventViewModel::new);
        allMessagesData = new ReadOnlyListWrapper<>(eventViewModels);
    }

    public ListProperty<LogEventViewModel> allMessagesDataProperty() {
        return this.allMessagesData;
    }

    /**
     * Concatenates the formatted message of the given {@link LogEvent}s by using the a new line separator.
     *
     * @return all messages as String
     */
    private String getLogMessagesAsString(List<LogEventViewModel> messages) {
        return messages.stream()
                .map(LogEventViewModel::getDetailedText)
                .collect(Collectors.joining(OS.NEWLINE));
    }

    /**
     * Copies the whole log to the clipboard
     */
    public void copyLog() {
        copyLog(allMessagesData);
    }

    /**
     * Copies the given list of {@link LogEvent}s to the clipboard.
     */
    public void copyLog(List<LogEventViewModel> messages) {
        if (messages.isEmpty()) {
            return;
        }
        clipBoardManager.setClipboardContents(getLogMessagesAsString(messages));
        dialogService.notify(Localization.lang("Log copied to clipboard."));
    }

    /**
     * Opens a new issue on GitHub and copies log to clipboard.
     */
    public void reportIssue() {
        try {
            String issueTitle = "Automatic Bug Report-" + dateFormat.format(date);
            String issueBody = String.format("JabRef %s%n%s %s %s %nJava %s\n\n", buildInfo.getVersion(), BuildInfo.OS,
                    BuildInfo.OS_VERSION, BuildInfo.OS_ARCH, BuildInfo.JAVA_VERSION);
            dialogService.notify(Localization.lang("Issue on GitHub successfully reported."));
            dialogService.showInformationDialogAndWait(Localization.lang("Issue report successful"),
                    Localization.lang("Your issue was reported in your browser.") + "\n" +
                            Localization.lang("The log and exception information was copied to your clipboard.") + " " +
                            Localization.lang("Please paste this information (with Ctrl+V) in the issue description."));
            URIBuilder uriBuilder = new URIBuilder()
                    .setScheme("https").setHost("github.com")
                    .setPath("/JabRef/jabref/issues/new")
                    .setParameter("title", issueTitle)
                    .setParameter("body", issueBody);
            JabRefDesktop.openBrowser(uriBuilder.build().toString());

            // Append log messages in issue description
            String issueDetails = "<details>\n" + "<summary>" + "Detail information:" + "</summary>\n\n```\n"
                    + getLogMessagesAsString(allMessagesData) + "\n```\n\n</details>";
            clipBoardManager.setClipboardContents(issueDetails);
        } catch (IOException | URISyntaxException e) {
            LOGGER.error(e);
        }
    }
}
