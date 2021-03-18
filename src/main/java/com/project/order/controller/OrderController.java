package com.project.order.controller;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.project.order.service.OrderService;
import com.project.order.service.ProductsOrderedService;
import com.project.order.dto.CartDTO;
import com.project.order.dto.OrderDTO;
import com.project.order.dto.OrderDetailsDTO;
import com.project.order.dto.ProductDTO;
import com.project.order.dto.ProductsOrderedDTO;
import com.project.order.dto.BuyerDTO;
import com.project.order.entity.OrderDetails;
import com.project.order.entity.ProductsOrdered;
import com.project.order.repository.OrderRepository;
import com.project.order.repository.ProductsOrderedRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@RestController
@CrossOrigin
public class OrderController {

	Logger logger = LoggerFactory.getLogger(this.getClass());
	
	@Autowired
	Environment environment;
	
	@Autowired
	OrderService orderService;
	
	@Autowired
	OrderRepository orderRepo;
	
	@Autowired
	ProductsOrderedRepository productsOrderedRepo;
	
	@Autowired
	ProductsOrderedService productsOrderedService;
	
	@Value("${product.uri}")
	String productUri;
	
	@Value("${buyer.uri}")
	String buyerUri;
	
	@Value("${buyer2.uri}")
	String buyerUri2;
	
	@Value("${cart.uri}")
	String cartUri;
	
	//Get All Orders
	@GetMapping(value = "/orders", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<List<OrderDetailsDTO>> getAllOrders() throws Exception{
		logger.info("Fetching all Orders");
		ResponseEntity<List<OrderDetailsDTO>> response = null;
		try {
			List<OrderDetailsDTO> ordersList = orderService.getAllOrders();
			response = new ResponseEntity<>(ordersList,HttpStatus.OK);
		}catch(Exception e){
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,environment.getProperty(e.getMessage()),e);
		}
		return response;
	}
	
	//Get Order by Order Id
	@GetMapping(value="/orders/{orderid}",produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<OrderDTO> getOrderById(@PathVariable Integer orderid) throws Exception{
		logger.info("Fetching a particular order for id {}",orderid);
		ResponseEntity<OrderDTO> response = null;
		try {
			OrderDTO orderDTO = orderService.getOrderById(orderid);
			response = new ResponseEntity<>(orderDTO,HttpStatus.OK);
		}catch(Exception e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,environment.getProperty(e.getMessage()),e);
		}
		return response;
		
	}
	
	//Place Order
	@PostMapping(value="/orders/placeOrder",consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> placeOrder(@RequestBody OrderDetailsDTO orderdetailsDTO) throws Exception{
		ResponseEntity<String> response = null;
		if(orderdetailsDTO.getADDRESS().length() > 100) {
			response = new ResponseEntity<>("Controller.INVALID_ADDRESS",HttpStatus.BAD_REQUEST);
			return response;
		}
		
		RestTemplate restTemplate = new RestTemplate();
		int buyerId = orderdetailsDTO.getBUYERID();
		
		ResponseEntity<CartDTO[]> cartDTO = restTemplate.getForEntity(cartUri+"getcart/"+buyerId, CartDTO[].class);
		List <CartDTO> cartList = Arrays.asList(cartDTO.getBody());
		
		if(!cartList.isEmpty()) {
			BuyerDTO buyerdto = restTemplate.getForObject(buyerUri+buyerId, BuyerDTO.class);
			String rewardPoints = buyerdto.getRewardPoints();
			int rewardPointsInt = Integer.parseInt(rewardPoints);
			double discount = (double)(rewardPointsInt/4);
			double amount = 0.0;
			for(CartDTO cartDTO1:cartList) {
				int prodId = cartDTO1.getProdId();
				ProductDTO productDTO = restTemplate.getForObject(productUri+prodId, ProductDTO.class);
				double price = productDTO.getPrice();
				int cartQty = cartDTO1.getQuantity();
				int productStock = productDTO.getStock();
				if(cartQty > productStock) {
					response = new ResponseEntity<>("Controller.INVALID_STOCK",HttpStatus.BAD_REQUEST);
					return response;
				}else {
					int qty = productStock-cartQty;
					ProductDTO pdto = new ProductDTO();
					pdto.setProdId(prodId);
					pdto.setStock(qty);
					restTemplate.put(productUri+"updatestock", pdto);
				}
				amount += price*cartQty;				
			}
			amount -= discount;
			int updatedRewardPoints = (int)(amount/100);
			updatedRewardPoints -= rewardPointsInt;
			String updatedRPs = Integer.toString(Math.abs(updatedRewardPoints));
	
			BuyerDTO bdto = new BuyerDTO();
			bdto.setBuyerId(buyerId);
			bdto.setRewardPoints(updatedRPs);
			restTemplate.put(buyerUri2, bdto);
		
			OrderDetailsDTO newOrderDetailsDTO = new OrderDetailsDTO();
			newOrderDetailsDTO.setBUYERID(orderdetailsDTO.getBUYERID());
			newOrderDetailsDTO.setADDRESS(orderdetailsDTO.getADDRESS());
			newOrderDetailsDTO.setAMOUNT(amount);
			newOrderDetailsDTO.setDATE(LocalDate.now());
			newOrderDetailsDTO.setSTATUS("ORDER PLACED");
		
	        OrderDetails orderdetails = newOrderDetailsDTO.createEntity();
	        orderRepo.save(orderdetails);
	        
	        for(CartDTO cartDTO2:cartList) {
				int prodId = cartDTO2.getProdId();
				ProductDTO productDTO = restTemplate.getForObject(productUri+prodId, ProductDTO.class);
				
		        ProductsOrderedDTO newProductOrderedDTO = new ProductsOrderedDTO();
		        newProductOrderedDTO.setORDERID(orderdetails.getORDERID());
		        newProductOrderedDTO.setPRODID(prodId);
		        newProductOrderedDTO.setSELLERID(productDTO.getSellerId());
		        newProductOrderedDTO.setQUANTITY(cartDTO2.getQuantity());
		        newProductOrderedDTO.setPRICE(productDTO.getPrice());
		        newProductOrderedDTO.setSTATUS("ORDER PLACED");
		        
		        ProductsOrdered productOrdered = newProductOrderedDTO.createEntity();
		        productsOrderedRepo.save(productOrdered);
		        
	        }
	        
	        for(CartDTO cartDTO3:cartList) {
	        	 HttpEntity<CartDTO> httpEntity = new HttpEntity<CartDTO>(cartDTO3);	
				 restTemplate.exchange(cartUri+"cart/remove", HttpMethod.DELETE, httpEntity,Void.class);
	        }
	        response =new ResponseEntity<>("Order placed successfully!!!",HttpStatus.OK);
		}else {
			response = new ResponseEntity<>("Order could not be placed!!!",HttpStatus.BAD_REQUEST);
		}
        return response;
	}
	

	
	@PutMapping(value="/orders/seller/status",consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> updateStatus (@RequestBody ProductsOrderedDTO product) {
		Integer orderId=product.getORDERID();
		Integer prodId=product.getPRODID();
		String status= product.getSTATUS();
		String message =  productsOrderedService.updateStatus(orderId,prodId,status);
		ResponseEntity<String> response = new ResponseEntity<>(message,HttpStatus.OK);
		return response;
	}
	
	@GetMapping(value="/orders/reOrder/{orderid}/{buyerid}",produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> reOrder(@PathVariable int orderid, @PathVariable int buyerid) {
		
		String message = orderService.reOrder(orderid, buyerid);
		ResponseEntity<String> response = new ResponseEntity<>(message,HttpStatus.OK);
		return response;
	}

}
