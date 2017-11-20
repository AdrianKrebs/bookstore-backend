package ch.bfh.eadj.control;

import ch.bfh.eadj.dto.OrderInfo;
import ch.bfh.eadj.dto.OrderStatistic;
import ch.bfh.eadj.entity.Order;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.List;

public class OrderRepository extends AbstractRepository {

    @PersistenceContext
    EntityManager em;

    public OrderRepository() {
        super(Order.class);
    }


    public List<OrderInfo> findByNameAndYear(String name, Integer year) {
        TypedQuery<OrderInfo> query = em.createNamedQuery(Order.FIND_BY_CUSTOMER_AND_YEAR_QUERY.QUERY_NAME, OrderInfo.class);
        query.setParameter("year", year);
        query.setParameter("name", name);
        List<OrderInfo> resultList = query.getResultList();
        return resultList;
    }

    public OrderStatistic getStatisticByYear(Integer year) {
        TypedQuery<OrderStatistic> query = em.createNamedQuery(Order.STATISTIC_BY_YEAR_QUERY.QUERY_NAME, OrderStatistic.class);
        query.setParameter("year", year);
        OrderStatistic singleResult = query.getSingleResult();
        return singleResult;
    }


    @Override
    protected EntityManager getEntityManager() {
        return em;
    }
}