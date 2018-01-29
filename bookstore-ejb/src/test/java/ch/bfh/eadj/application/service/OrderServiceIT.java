package ch.bfh.eadj.application.service;

import ch.bfh.eadj.application.exception.*;
import ch.bfh.eadj.persistence.dto.OrderInfo;
import ch.bfh.eadj.persistence.entity.Book;
import ch.bfh.eadj.persistence.entity.Customer;
import ch.bfh.eadj.persistence.entity.Order;
import ch.bfh.eadj.persistence.entity.OrderItem;
import ch.bfh.eadj.persistence.enumeration.OrderStatus;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.naming.Context;
import javax.naming.InitialContext;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.testng.Assert.*;

public class OrderServiceIT extends AbstractServiceIT {

    private static final String ORDER_SERVICE_NAME = "java:global/bookstore-app-1.0-SNAPSHOT/bookstore/OrderService!ch.bfh.eadj.application.service.OrderServiceRemote";
    private static final String CATALOG_SERVICE_NAME = "java:global/bookstore-app-1.0-SNAPSHOT/bookstore/CatalogService!ch.bfh.eadj.application.service.CatalogServiceRemote";
    private static final String CUSTOMER_SERVICE_NAME = "java:global/bookstore-app-1.0-SNAPSHOT/bookstore/CustomerService!ch.bfh.eadj.application.service.CustomerServiceRemote";

    private CustomerServiceRemote customerService;
    private CatalogServiceRemote catalogService;
    private OrderServiceRemote orderService;

    private Book book;
    private Customer customer;
    private Order order;

    private Integer year = LocalDate.now().getYear();

    @BeforeClass
    public void setUp() throws Exception {
        Context jndiContext = new InitialContext();
        orderService = (OrderServiceRemote) jndiContext.lookup(ORDER_SERVICE_NAME);
        catalogService = (CatalogServiceRemote) jndiContext.lookup(CATALOG_SERVICE_NAME);
        customerService = (CustomerServiceRemote) jndiContext.lookup(CUSTOMER_SERVICE_NAME);
    }

    @Test(dependsOnMethods = "shouldPlaceOrder")
    public void shouldCancelOrder() throws Exception {
        //when
        order = orderService.findOrder(order.getNr());
        orderService.cancelOrder(order.getNr());
        order = orderService.findOrder(order.getNr());

        //then
        assertThat(order.getStatus(), is(OrderStatus.CANCELED));
    }


    @Test(dependsOnMethods = {"shouldPlaceOrder", "shouldCancelOrder"}, expectedExceptions = OrderAlreadyCanceledException.class)
    public void shouldFailCancelOrder() throws Exception {
        //given
        order = orderService.findOrder(order.getNr());
        assertThat(order.getStatus(), is(OrderStatus.CANCELED));

            //when
            orderService.cancelOrder(order.getNr());

            //then
            fail("OrderAlreadyCanceledException exception");
    }

    @Test(dependsOnMethods = {"shouldPlaceOrder", "shouldFailCancelOrder"}, expectedExceptions = OrderAlreadyShippedException.class)
    public void shouldFailCancelShippedOrder() throws Exception {
        //given
        List<OrderItem> items = createOrderItems(4, book);
        customer = customerService.findCustomer(customer.getNr());
        customer.getCreditCard().setExpirationYear(year);
        customer.getCreditCard().setNumber("1111222233334444");
        customerService.updateCustomer(customer);
        order = orderService.placeOrder(customer, items);

        Thread.sleep(20000);
        order = orderService.findOrder(order.getNr());

            //when
            orderService.cancelOrder(order.getNr());

            //then
            fail("OrderAlreadyShippedException exception");
    }

    @Test(dependsOnMethods = "shouldPlaceOrder")
    public void shouldFindOrder() throws Exception {
        //when
        Order orderFromDb = orderService.findOrder(order.getNr());

        //then
        assertEquals(orderFromDb.getAmount(), order.getAmount());
        assertEquals(orderFromDb.getStatus(), order.getStatus());
        assertEquals(orderFromDb.getCustomer(), order.getCustomer());
    }

    @Test(dependsOnMethods = "shouldPlaceOrder", expectedExceptions = OrderNotFoundException.class)
    public void shouldFailFindOrder() throws OrderNotFoundException {
            //when
            orderService.findOrder(222L);

            //then
            fail("OrderNotFoundException exception");
    }

    @Test
    public void shouldPlaceOrder() throws Exception {
        //given
        book = createBook("test", "12345", "max muster");
        catalogService.addBook(book);
        book = catalogService.findBook(book.getIsbn());
        List<OrderItem> items = createOrderItems(3, book);
        customer = createCustomer();
        Long userId = customerService.registerCustomer(customer, "pwd");
        customer = customerService.findCustomer(userId);

        //when
        order = orderService.placeOrder(customer, items);

        //then
        assertThat(order.getStatus(), is(OrderStatus.ACCEPTED));
        assertEquals(order.getCustomer().getEmail(), customer.getEmail());
    }

    @Test(dependsOnMethods = "shouldPlaceOrder")
    public void shouldFailPlaceOrderLimitExceeded() throws Exception {
        //given
        customer = customerService.findCustomer(customer.getNr());
        List<OrderItem> items = createOrderItems(30, book);
        customer.getCreditCard().setExpirationYear(year);
        customer.getCreditCard().setNumber("1111222233334444");
        try {
            //when
            order = orderService.placeOrder(customer, items);

            //then
            fail("PaymentFailedException exception");
        } catch (PaymentFailedException e) {
            assertTrue(e.getCode().equals(PaymentFailedException.Code.PAYMENT_LIMIT_EXCEEDED));
        }
    }

    @Test(dependsOnMethods = "shouldFailPlaceOrderLimitExceeded")
    public void shouldFailPlaceOrderExpiredCreditCard() throws Exception {
        //given
        customer = customerService.findCustomer(customer.getNr());
        List<OrderItem> items = createOrderItems(5, book);
        customer.getCreditCard().setExpirationYear(2016);
        customer.getCreditCard().setNumber("1111222233334444");
        customerService.updateCustomer(customer);
        try {
            //when
            order = orderService.placeOrder(customer, items);

            //then
            fail("PaymentFailedException exception");
        } catch (PaymentFailedException e) {
            assertTrue(e.getCode().equals(PaymentFailedException.Code.CREDIT_CARD_EXPIRED));
        }
    }

    @Test(dependsOnMethods = "shouldFailPlaceOrderExpiredCreditCard")
    public void shouldFailPlaceOrderInvalidCard() throws Exception {
        //given
        customer = customerService.findCustomer(customer.getNr());
        List<OrderItem> items = createOrderItems(5, book);
        customer.getCreditCard().setExpirationYear(year);
        customer.getCreditCard().setNumber("111122223333444");
        customerService.updateCustomer(customer);
        try {
            //when
            order = orderService.placeOrder(customer, items);

            //then
            fail("PaymentFailedException exception");
        } catch (PaymentFailedException e) {
            assertTrue(e.getCode().equals(PaymentFailedException.Code.INVALID_CREDIT_CARD));
        }
    }

    @Test(dependsOnMethods = "shouldPlaceOrder")
    public void shouldSearchOrders() throws Exception {
        //when
        List<OrderInfo> orderInfoList = orderService.searchOrders(customer, year);

        //then
        assertFalse(orderInfoList.isEmpty());
    }

    @Test(dependsOnMethods = "shouldPlaceOrder")
    public void shouldFailSearchOrders() {
        //when
        List<OrderInfo> orderInfoList = orderService.searchOrders(customer, 2015);

        //then
        assertTrue(orderInfoList.isEmpty());
    }

    @AfterClass
    public void tearDown() throws OrderNotFoundException, CustomerNotFoundException, BookNotFoundException {
        customer = customerService.findCustomer(customer.getNr());
        List<OrderInfo> orderInfoList = orderService.searchOrders(customer, year);
        for (OrderInfo orderInfo : orderInfoList) {
            order = orderService.findOrder(orderInfo.getNr());
            orderService.removeOrder(order);
            try {
                order = orderService.findOrder(order.getNr());
                fail("OrderNotFoundException exception");
            } catch (OrderNotFoundException e) {
                System.out.println("Expected exception: OrderNotFoundException");
            }
        }

        customerService.removeCustomer(customer);
        try {
            customer = customerService.findCustomer(customer.getNr());
            fail("CustomerNotFoundException exception");
        } catch (CustomerNotFoundException e) {
            System.out.println("Expected exception: CustomerNotFoundException");
        }

        book = catalogService.findBook(book.getIsbn());
        catalogService.removeBook(book);
        try {
            book = catalogService.findBook(book.getIsbn());
            fail("BookNotFoundException exception");
        } catch (BookNotFoundException e) {
            System.out.println("Expected exception: BookNotFoundException");
        }
    }
}