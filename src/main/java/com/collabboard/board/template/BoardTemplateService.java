package com.collabboard.board.template;

import com.collabboard.board.template.dto.BoardTemplateResponse;
import com.collabboard.common.exception.BadRequestException;
import com.collabboard.i18n.Messages;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Şablonların kişinin dilindeki hâli (ADR 0021).
 *
 * Liste de oluşturma da aynı dili kullanır: hesabın dili (users.locale).
 * Önizlemede "Yapılacak" görüp panoda "To Do" bulmak olmasın.
 */
@Service
public class BoardTemplateService {

    private final Messages messages;

    public BoardTemplateService(Messages messages) {
        this.messages = messages;
    }

    /** Pano açma penceresindeki liste; varsayılan şablon ilk sırada. */
    public List<BoardTemplateResponse> list(Locale locale) {
        return Arrays.stream(BoardTemplate.values())
                .map(t -> new BoardTemplateResponse(t.id(), messages.get(locale, t.nameKey()),
                        t == BoardTemplate.DEFAULT, columns(t, locale)))
                .toList();
    }

    /**
     * İstekteki şablon kimliği. Boşsa varsayılan; tanınmıyorsa 400 ve geçerli
     * değerler. Sessizce varsayılana düşmek, yazım hatasını "işe yaradı" gibi gösterirdi.
     */
    public BoardTemplate resolve(String templateId) {
        if (templateId == null || templateId.isBlank()) {
            return BoardTemplate.DEFAULT;
        }
        return BoardTemplate.byId(templateId)
                .orElseThrow(() -> new BadRequestException("error.template.unknown", BoardTemplate.ids()));
    }

    /** Şablonun kolonları, verilen dilde, soldan sağa. */
    public List<BoardTemplateResponse.Column> columns(BoardTemplate template, Locale locale) {
        return template.columns().stream()
                .map(c -> new BoardTemplateResponse.Column(messages.get(locale, template.columnKey(c)), c.wipLimit()))
                .toList();
    }
}
