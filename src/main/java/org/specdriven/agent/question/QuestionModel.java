package org.specdriven.agent.question;

import com.lealone.orm.Model;
import com.lealone.orm.ModelProperty;
import com.lealone.orm.ModelTable;
import com.lealone.orm.property.PLong;
import com.lealone.orm.property.PString;

/**
 * Minimal ORM mapping for the existing questions table.
 */
final class QuestionModel extends Model<QuestionModel> {

    private static final String DATABASE_NAME = "AGENT_DB";
    private static final String SCHEMA_NAME = "PUBLIC";
    private static final String TABLE_NAME = "QUESTIONS";

    public final PString<QuestionModel> questionId;
    public final PString<QuestionModel> sessionId;
    public final PString<QuestionModel> questionText;
    public final PString<QuestionModel> impact;
    public final PString<QuestionModel> recommendation;
    public final PString<QuestionModel> status;
    public final PString<QuestionModel> category;
    public final PString<QuestionModel> deliveryMode;
    public final PLong<QuestionModel> createdAt;
    public final PLong<QuestionModel> updatedAt;

    QuestionModel(String jdbcUrl) {
        this(table(jdbcUrl), REGULAR_MODEL);
    }

    static QuestionModel dao(String jdbcUrl) {
        return new QuestionModel(table(jdbcUrl), ROOT_DAO);
    }

    private QuestionModel(ModelTable table, short modelType) {
        super(table, modelType);
        questionId = new PString<>("QUESTION_ID", this);
        sessionId = new PString<>("SESSION_ID", this);
        questionText = new PString<>("QUESTION_TEXT", this);
        impact = new PString<>("IMPACT", this);
        recommendation = new PString<>("RECOMMENDATION", this);
        status = new PString<>("STATUS", this);
        category = new PString<>("CATEGORY", this);
        deliveryMode = new PString<>("DELIVERY_MODE", this);
        createdAt = new PLong<>("CREATED_AT", this);
        updatedAt = new PLong<>("UPDATED_AT", this);
        super.setModelProperties(new ModelProperty[] {
                questionId,
                sessionId,
                questionText,
                impact,
                recommendation,
                status,
                category,
                deliveryMode,
                createdAt,
                updatedAt
        });
    }

    @Override
    protected QuestionModel newInstance(ModelTable table, short modelType) {
        return new QuestionModel(table, modelType);
    }

    private static ModelTable table(String jdbcUrl) {
        return new ModelTable(jdbcUrl, DATABASE_NAME, SCHEMA_NAME, TABLE_NAME);
    }
}
