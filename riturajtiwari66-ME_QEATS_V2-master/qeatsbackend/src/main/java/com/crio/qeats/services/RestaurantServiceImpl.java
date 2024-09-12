

/*
 *
 *  * Copyright (c) Crio.Do 2019. All rights reserved
 *
 */

package com.crio.qeats.services;

import com.crio.qeats.dto.Restaurant;
import com.crio.qeats.exchanges.GetRestaurantsRequest;
import com.crio.qeats.exchanges.GetRestaurantsResponse;
import com.crio.qeats.repositoryservices.RestaurantRepositoryService;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class RestaurantServiceImpl implements RestaurantService {

  private final Double peakHoursServingRadiusInKms = 3.0;
  private final Double normalHoursServingRadiusInKms = 5.0;
  private int numThreads = 5;
  @Autowired
  private RestaurantRepositoryService restaurantRepositoryService;


  // TODO: CRIO_TASK_MODULE_RESTAURANTSAPI - Implement findAllRestaurantsCloseby.
  // Check RestaurantService.java file for the interface contract.
  @Override
  public GetRestaurantsResponse findAllRestaurantsCloseBy(
      GetRestaurantsRequest getRestaurantsRequest, LocalTime currentTime) {
      List<Restaurant> restaurants ;
      Double servingRadiusInKm = isPeakHour(currentTime) ? peakHoursServingRadiusInKms : normalHoursServingRadiusInKms;
      restaurants = restaurantRepositoryService.
      findAllRestaurantsCloseBy(getRestaurantsRequest.getLatitude(), getRestaurantsRequest.getLongitude(), currentTime, servingRadiusInKm);
      GetRestaurantsResponse getRestaurantResponse = new GetRestaurantsResponse(restaurants);
      log.info(getRestaurantResponse);
    
    return getRestaurantResponse;
  }

  
  private boolean isPeakHour(LocalTime timeNow) {
    return isTimeWithInRange(timeNow, LocalTime.of(7, 59, 59), LocalTime.of(10, 00, 01))
        || isTimeWithInRange(timeNow, LocalTime.of(12, 59, 59), LocalTime.of(14, 00, 01))
        || isTimeWithInRange(timeNow, LocalTime.of(18, 59, 59), LocalTime.of(21, 00, 01));
  }

  private boolean isTimeWithInRange(LocalTime timeNow,
    LocalTime startTime, LocalTime endTime) {
    return timeNow.isAfter(startTime) && timeNow.isBefore(endTime);
  }


  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Implement findRestaurantsBySearchQuery. The request object has the search string.
  // We have to combine results from multiple sources:
  // 1. Restaurants by name (exact and inexact)
  // 2. Restaurants by cuisines (also called attributes)
  // 3. Restaurants by food items it serves
  // 4. Restaurants by food item attributes (spicy, sweet, etc)
  // Remember, a restaurant must be present only once in the resulting list.
  // Check RestaurantService.java file for the interface contract.
  @Override
  public GetRestaurantsResponse findRestaurantsBySearchQuery(
      GetRestaurantsRequest getRestaurantsRequest, LocalTime currentTime) {
    
        String searchFor = getRestaurantsRequest.getSearchFor();
        Double latitude = getRestaurantsRequest.getLatitude();
        Double longitude = getRestaurantsRequest.getLongitude();
        Double servingRadiusInKm = isPeakHour(currentTime) ? peakHoursServingRadiusInKms : normalHoursServingRadiusInKms;
        
        final Long startTime = System.currentTimeMillis();
        if(searchFor.isEmpty()) return new GetRestaurantsResponse(Collections.emptyList());
        // Find restaurants by name
        List<Restaurant> restaurantsByName = restaurantRepositoryService.findRestaurantsByName(latitude, longitude, searchFor, currentTime, servingRadiusInKm);
        
        // Find restaurants by cuisines
        List<Restaurant> restaurantsByCuisines = restaurantRepositoryService.findRestaurantsByAttributes(latitude, longitude, searchFor, currentTime, servingRadiusInKm);
        
        // Find restaurants by food items served
        List<Restaurant> restaurantsByFoodItems = restaurantRepositoryService.findRestaurantsByItemName(latitude, longitude, searchFor, currentTime, servingRadiusInKm);
        
        // Find restaurants by food item attributes
        List<Restaurant> restaurantsByFoodItemAttributes = restaurantRepositoryService.findRestaurantsByItemAttributes(latitude, longitude, searchFor, currentTime, servingRadiusInKm);
        
        
        Long endTime = System.currentTimeMillis() - startTime;
        System.out.println("Total time taken is " + endTime + " ms.");
        // Combine all the lists while ensuring uniqueness
        LinkedHashSet<Restaurant> combinedRestaurants = new LinkedHashSet<>();
        combinedRestaurants.addAll(restaurantsByName);
        combinedRestaurants.addAll(restaurantsByCuisines);
        combinedRestaurants.addAll(restaurantsByFoodItems);
        combinedRestaurants.addAll(restaurantsByFoodItemAttributes);
        
        // Convert the set back to a list
        List<Restaurant> combinedRestaurantList = new ArrayList<>(combinedRestaurants);
        
        return new GetRestaurantsResponse(combinedRestaurantList);
  }



  // TODO: CRIO_TASK_MODULE_MULTITHREADING
  // Implement multi-threaded version of RestaurantSearch.
  // Implement variant of findRestaurantsBySearchQuery which is at least 1.5x time faster than
  // findRestaurantsBySearchQuery.
  @Override
  public GetRestaurantsResponse findRestaurantsBySearchQueryMt(
      GetRestaurantsRequest getRestaurantsRequest, LocalTime currentTime) {
        
        List<List<Restaurant>> restaurants = new ArrayList<>();
        String searchFor = getRestaurantsRequest.getSearchFor();
        Double latitude = getRestaurantsRequest.getLatitude();
        Double longitude = getRestaurantsRequest.getLongitude();
        Double servingRadiusInKm = isPeakHour(currentTime) ? peakHoursServingRadiusInKms : normalHoursServingRadiusInKms;

        final Long startTime = System.currentTimeMillis();
        if(searchFor.isEmpty()) return new GetRestaurantsResponse(Collections.emptyList());

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<List<Restaurant>>> futures = new ArrayList<>();

        Future<List<Restaurant>> future = executor.submit(() -> {
          return this.restaurantRepositoryService.findRestaurantsByName(latitude, longitude, 
          searchFor, currentTime, servingRadiusInKm);
        });
        futures.add(future);

        future = executor.submit(() -> {
          return this.restaurantRepositoryService.findRestaurantsByAttributes(latitude, longitude, 
          searchFor, currentTime, servingRadiusInKm);
        });
        futures.add(future);

        future = executor.submit(() -> {
          return this.restaurantRepositoryService.findRestaurantsByItemName(latitude, longitude, 
          searchFor, currentTime, servingRadiusInKm);
        });
        futures.add(future);

        future = executor.submit(() -> {
          return this.restaurantRepositoryService.findRestaurantsByItemAttributes(latitude, longitude, 
          searchFor, currentTime, servingRadiusInKm);
        });
        futures.add(future);


        for(Future<List<Restaurant>> fut : futures){
          try {
            restaurants.add(fut.get());
          } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          } 
        }

        long endTime = System.currentTimeMillis() - startTime;
        System.out.println("time taken is " + endTime + " ms.");
        
        Set<String> set = new HashSet<>();
        List<Restaurant> result = new ArrayList<>();

        for(List<Restaurant> restro : restaurants){
          for(Restaurant res : restro){
            if(!set.contains(res.getId())){
              set.add(res.getId());
              result.add(res);
            }
          }
        }


     return new GetRestaurantsResponse(result);
  }
}

