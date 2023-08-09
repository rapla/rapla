package org.rapla.plugin.exchangeconnector;

import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Permission;
import org.rapla.storage.StorageOperator;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ShowExchangeForUser {

    StorageOperator operator;
    @Inject
    public ShowExchangeForUser(StorageOperator operator) {
        this.operator = operator;
    }

    public boolean isExchangeEnabledFor(User user) {
        Category groupCategory = operator.getSuperCategory().getCategory(Permission.GROUP_CATEGORY_KEY);
        Category exchangeSynchronizationGroup = groupCategory != null ? groupCategory.getCategory(ExchangeConnectorPlugin.EXCHANGE_SYNCHRONIZATION_GROUP) :  null;
        boolean enabled = new ExchangeConnectorConfig.ConfigReader(operator).isEnabled();
        return  enabled && user != null && ( exchangeSynchronizationGroup == null || user.belongsTo( exchangeSynchronizationGroup));
    }

}
