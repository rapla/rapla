package org.rapla.client.base;

public abstract class AbstractView<P> implements View<P> {

    private P presenter;

    @Override
    public void setPresenter(P presenter) {
        this.presenter = presenter;
    }
    
    protected P getPresenter() {
        return presenter;
    }
    

    
}
