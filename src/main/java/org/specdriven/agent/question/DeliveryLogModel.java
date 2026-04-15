package org.specdriven.agent.question;

import com.lealone.db.value.Value;
import com.lealone.db.value.ValueNull;
import com.lealone.orm.Model;
import com.lealone.orm.ModelProperty;
import com.lealone.orm.ModelTable;
import com.lealone.orm.property.PInteger;
import com.lealone.orm.property.PLong;
import com.lealone.orm.property.PString;

/**
 * Minimal ORM mapping for the existing delivery_log table.
 */
final class DeliveryLogModel extends Model<DeliveryLogModel> {

    private static final String DATABASE_NAME = "AGENT_DB";
    private static final String SCHEMA_NAME = "PUBLIC";
    private static final String TABLE_NAME = "DELIVERY_LOG";

    public final PLong<DeliveryLogModel> id;
    public final PString<DeliveryLogModel> questionId;
    public final PString<DeliveryLogModel> channelType;
    public final PInteger<DeliveryLogModel> attemptNumber;
    public final PString<DeliveryLogModel> status;
    public final PInteger<DeliveryLogModel> statusCode;
    public final PString<DeliveryLogModel> errorMessage;
    public final PLong<DeliveryLogModel> attemptedAt;

    DeliveryLogModel(String jdbcUrl) {
        this(table(jdbcUrl), REGULAR_MODEL);
    }

    static DeliveryLogModel dao(String jdbcUrl) {
        return new DeliveryLogModel(table(jdbcUrl), ROOT_DAO);
    }

    private DeliveryLogModel(ModelTable table, short modelType) {
        super(table, modelType);
        id = new PLong<>("ID", this);
        questionId = new PString<>("QUESTION_ID", this);
        channelType = new PString<>("CHANNEL_TYPE", this);
        attemptNumber = new PInteger<>("ATTEMPT_NUMBER", this);
        status = new PString<>("STATUS", this);
        statusCode = new NullableIntegerProperty("STATUS_CODE", this);
        errorMessage = new PString<>("ERROR_MESSAGE", this);
        attemptedAt = new PLong<>("ATTEMPTED_AT", this);
        super.setModelProperties(new ModelProperty[] {
                id,
                questionId,
                channelType,
                attemptNumber,
                status,
                statusCode,
                errorMessage,
                attemptedAt
        });
    }

    @Override
    protected DeliveryLogModel newInstance(ModelTable table, short modelType) {
        return new DeliveryLogModel(table, modelType);
    }

    private static ModelTable table(String jdbcUrl) {
        return new ModelTable(jdbcUrl, DATABASE_NAME, SCHEMA_NAME, TABLE_NAME);
    }

    private static final class NullableIntegerProperty extends PInteger<DeliveryLogModel> {
        private NullableIntegerProperty(String name, DeliveryLogModel model) {
            super(name, model);
        }

        @Override
        protected void deserialize(Value value) {
            if (value == ValueNull.INSTANCE || value.getType() == Value.NULL) {
                this.value = null;
                return;
            }
            super.deserialize(value);
        }
    }
}
