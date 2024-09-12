/*
 *
 *  * Copyright (c) Crio.Do 2019. All rights reserved
 *
 */

package com.crio.qeats.repositoryservices;

import ch.hsr.geohash.GeoHash;
import com.crio.qeats.configs.RedisConfiguration;
import com.crio.qeats.dto.Item;
import com.crio.qeats.dto.Restaurant;
import com.crio.qeats.globals.GlobalConstants;
import com.crio.qeats.models.ItemEntity;
import com.crio.qeats.models.MenuEntity;
import com.crio.qeats.models.RestaurantEntity;
import com.crio.qeats.repositories.ItemRepository;
import com.crio.qeats.repositories.MenuRepository;
import com.crio.qeats.repositories.RestaurantRepository;
import com.crio.qeats.utils.GeoLocation;
import com.crio.qeats.utils.GeoUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Provider;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;


@Service
@Primary
public class RestaurantRepositoryServiceImpl implements RestaurantRepositoryService {

  private final RestaurantRepository restaurantRepository;
  private final MenuRepository menuRepository;
  private final ItemRepository itemRepository;
  
  public RestaurantRepositoryServiceImpl(RestaurantRepository restaurantRepository, MenuRepository menuRepository ,ItemRepository itemRepository) {
    this.restaurantRepository = restaurantRepository;
    this.menuRepository = menuRepository;
    this.itemRepository = itemRepository;
  }




  @Autowired
  private RedisConfiguration redisConfiguration;

  @Autowired
  private MongoTemplate mongoTemplate;

  @Autowired
  private Provider<ModelMapper> modelMapperProvider;

  private boolean isOpenNow(LocalTime time, RestaurantEntity res) {
    LocalTime openingTime = LocalTime.parse(res.getOpensAt());
    LocalTime closingTime = LocalTime.parse(res.getClosesAt());

    return time.isAfter(openingTime) && time.isBefore(closingTime);
  }

  // // TODO: CRIO_TASK_MODULE_NOSQL
  // // Objectives:
  // // 1. Implement findAllRestaurantsCloseby.
  // // 2. Remember to keep the precision of GeoHash in mind while using it as a key.
  // // Check RestaurantRepositoryService.java file for the interface contract.
  public List<Restaurant> findAllRestaurantsCloseByFromDb(Double latitude,
      Double longitude, LocalTime currentTime, Double servingRadiusInKms) {
    
    List<RestaurantEntity> restaurantEntities = restaurantRepository.findAll();
    ModelMapper modelMapper = modelMapperProvider.get();
    List<Restaurant> restaurants = new ArrayList<>();
    
    
      //CHECKSTYLE:OFF
    for(RestaurantEntity restaurantEntity : restaurantEntities){
      if(isRestaurantCloseByAndOpen(restaurantEntity, currentTime, latitude, longitude, servingRadiusInKms)){
        restaurants.add(modelMapper.map(restaurantEntity, Restaurant.class));
      }
    }

    return restaurants;
  }
  
  public List<Restaurant> findAllRestaurantsCloseBy(Double latitude,
      Double longitude, LocalTime currentTime, Double servingRadiusInKms) {

    List<Restaurant> restaurants = null;
    // TODO: CRIO_TASK_MODULE_REDIS
    // We want to use cache to speed things up. Write methods that perform the same functionality,
    // but using the cache if it is present and reachable.
    // Remember, you must ensure that if cache is not present, the queries are directed at the
    // database instead.

      //CHECKSTYLE:OFF
     if(redisConfiguration.isCacheAvailable()){
      restaurants = findAllRestaurantsCloseByFromCache(latitude,longitude,currentTime,servingRadiusInKms);
    }else{
      restaurants = findAllRestaurantsCloseByFromDb(latitude, longitude, currentTime, servingRadiusInKms);
    }
      //CHECKSTYLE:ON
    return restaurants;
  }




  private List<Restaurant> findAllRestaurantsCloseByFromCache(Double latitude,
   Double longitude, LocalTime currentTime, Double servingRadiusInKms){
    
    List<Restaurant> restaurants = new ArrayList<>();

    GeoLocation geoLocation = new GeoLocation(latitude, longitude);
    GeoHash geoHash = GeoHash.withCharacterPrecision(geoLocation.getLatitude(),geoLocation.getLongitude(),7);

    
    try(Jedis jedis = redisConfiguration.getJedisPool().getResource()){
      String jsonStringFromCache = jedis.get(geoHash.toBase32());

      if(jsonStringFromCache == null){
        String newJsonString = "";
        
        try{
          restaurants = findAllRestaurantsCloseByFromDb(geoLocation.getLatitude(),geoLocation.getLongitude(), currentTime, servingRadiusInKms);
          newJsonString = new ObjectMapper().writeValueAsString(restaurants);
        }catch(JsonProcessingException e){
          e.printStackTrace();
        }

        jedis.setex(geoHash.toBase32(), GlobalConstants.REDIS_ENTRY_EXPIRY_IN_SECONDS, newJsonString);

      }else{

        try {
          restaurants = new ObjectMapper().readValue(jsonStringFromCache, new TypeReference<List<Restaurant>>() {
          });
        } catch (IOException e) {
          e.printStackTrace();
        }

      }
    }
   
    return restaurants; 
  }

  

  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants whose names have an exact or partial match with the search query.
  @Override
  public List<Restaurant> findRestaurantsByName(Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {
     
        List<RestaurantEntity> restaurantsByNameExact = restaurantRepository.findRestaurantsByNameExact(searchString).orElse(Collections.emptyList());

        List<RestaurantEntity> restaurantsByPartialName = restaurantRepository.findRestaurantsByPartialName(searchString).orElse(Collections.emptyList());
        
        Set<String> restaurantsByName = new HashSet<>();

        ModelMapper modelMapper = modelMapperProvider.get();
        
        List<Restaurant> ansRestro = new ArrayList<>();
        
       
        for(RestaurantEntity restrEntity : restaurantsByNameExact){
            if(isRestaurantCloseByAndOpen(restrEntity,currentTime, latitude,longitude,servingRadiusInKms)){
              if(!restaurantsByName.contains(restrEntity.getId())){
                restaurantsByName.add(restrEntity.getId());
                ansRestro.add(modelMapper.map(restrEntity , Restaurant.class));
              }
            }
        }
        

        
        for(RestaurantEntity restrEntity : restaurantsByPartialName){
            if(isRestaurantCloseByAndOpen(restrEntity,currentTime, latitude,longitude,servingRadiusInKms)){
              if(!restaurantsByName.contains(restrEntity.getId())){
                restaurantsByName.add(restrEntity.getId());
                ansRestro.add(modelMapper.map(restrEntity , Restaurant.class));
              }
            }
        }
        
      
      return ansRestro;
  }
 

  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants whose attributes (cuisines) intersect with the search query.
  @Override
  public List<Restaurant> findRestaurantsByAttributes(
      Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {

       
        List<RestaurantEntity> restaurantEntitiesByCuisine = restaurantRepository.findRestaurantsByCuisine(searchString).orElse(Collections.emptyList());
        ModelMapper modelMapper = modelMapperProvider.get();
        Set<String> restaurantsByAttributes = new HashSet<>();
        List<Restaurant> restaurants = new ArrayList<>();
       
        for(RestaurantEntity restaurantEntity : restaurantEntitiesByCuisine){
          if(isRestaurantCloseByAndOpen(restaurantEntity,currentTime, latitude,longitude,servingRadiusInKms)){
            if(!restaurantsByAttributes.contains(restaurantEntity.getId())){
              restaurantsByAttributes.add(restaurantEntity.getId());
              restaurants.add(modelMapper.map(restaurantEntity , Restaurant.class));
            }
          }
        }
        
        return restaurants;
  }
  


  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants which serve food items whose names form a complete or partial match
  // with the search query.

  @Override
  public List<Restaurant> findRestaurantsByItemName(
      Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {

         // Find all restaurants within the specified radius
        List<Restaurant> nearbyRestaurants = findAllRestaurantsCloseBy(latitude, longitude, currentTime, servingRadiusInKms);

        // Find menu entities matching the search string exactly
        List<MenuEntity> exactMatchMenuEntities = menuRepository.findMenusByItemNameExact(searchString)
                .orElse(Collections.emptyList());

        // Extract unique restaurants ids from the exact match menu entities
        Set<String> exactMatchRestaurantIds = exactMatchMenuEntities.stream()
                .map(MenuEntity::getRestaurantId)
                .collect(Collectors.toSet());

        // Filter nearby restaurants by exact matching restaurant IDs
        Set<Restaurant> restaurantsByExactMatch = nearbyRestaurants.stream()
                .filter(restaurant -> exactMatchRestaurantIds.contains(restaurant.getId()))
                .collect(Collectors.toSet());

        // Find menu entities matching the search string partially
        List<MenuEntity> partialMatchMenuEntities = menuRepository.findMenusByItemName(searchString)
                .orElse(Collections.emptyList());

        // Extract unique restaurant IDs from the partial match menu entities
        Set<String> partialMatchRestaurantIds = partialMatchMenuEntities.stream()
                .map(MenuEntity::getRestaurantId)
                .collect(Collectors.toSet());

        // Filter nearby restaurants by partial matching restaurant IDs
        Set<Restaurant> restaurantsByPartialMatch = nearbyRestaurants.stream()
                .filter(restaurant -> partialMatchRestaurantIds.contains(restaurant.getId()))
                .collect(Collectors.toSet());

        // Combine exact and partial matches with exact matches first
        Set<Restaurant> restaurantsByItemName = new HashSet<>();
        restaurantsByItemName.addAll(restaurantsByExactMatch);
        restaurantsByItemName.addAll(restaurantsByPartialMatch);

        return new ArrayList<>(restaurantsByItemName);
  }

  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants which serve food items whose attributes intersect with the search query.
  @Override
  public List<Restaurant> findRestaurantsByItemAttributes(Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {

     return null;
  }





  /**
   * Utility method to check if a restaurant is within the serving radius at a given time.
   * @return boolean True if restaurant falls within serving radius and is open, false otherwise
   */
  private boolean isRestaurantCloseByAndOpen(RestaurantEntity restaurantEntity,
      LocalTime currentTime, Double latitude, Double longitude, Double servingRadiusInKms) {
    if (isOpenNow(currentTime, restaurantEntity)) {
      return GeoUtils.findDistanceInKm(latitude, longitude,
          restaurantEntity.getLatitude(), restaurantEntity.getLongitude())
          < servingRadiusInKms;
    }

    return false;
  }



}

