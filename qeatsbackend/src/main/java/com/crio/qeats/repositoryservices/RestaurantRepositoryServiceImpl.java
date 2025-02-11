/*
 *
 *  * Copyright (c) Crio.Do 2019. All rights reserved
 *
 */

package com.crio.qeats.repositoryservices;

import ch.hsr.geohash.GeoHash;
import com.crio.qeats.configs.RedisConfiguration;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Provider;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;


@Service
@Primary
public class RestaurantRepositoryServiceImpl implements RestaurantRepositoryService {



  @Autowired
  private RedisConfiguration redisConfiguration;

  @Autowired
  private MongoTemplate mongoTemplate;

  @Autowired
  private Provider<ModelMapper> modelMapperProvider;

  @Autowired 
  private RestaurantRepository restaurantRepository;

  @Autowired
  MenuRepository menuRepository;

  @Autowired
  ItemRepository itemRepository;


  private boolean isOpenNow(LocalTime time, RestaurantEntity res) {
    LocalTime openingTime = LocalTime.parse(res.getOpensAt());
    LocalTime closingTime = LocalTime.parse(res.getClosesAt());

    return time.isAfter(openingTime) && time.isBefore(closingTime);
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
    //CHECKSTYLE:ON
     
    if(redisConfiguration.isCacheAvailable())
    {
      restaurants = findAllRestaurantsCloseByFromCache(latitude,longitude, currentTime, servingRadiusInKms);
    }
    else
    {
      restaurants = findAllRestaurantsCloseFromDb(latitude, longitude, currentTime, servingRadiusInKms);
    }
      
    return restaurants;
  }
  


private List<Restaurant> findAllRestaurantsCloseFromDb(Double latitude,Double longitude, LocalTime currentTime,Double servingRadiusInKms) 
{
  ModelMapper modelMapper = modelMapperProvider.get();
  List<RestaurantEntity> restaurantEntities = restaurantRepository.findAll(); 
  List<Restaurant> restaurants = new ArrayList<Restaurant>();
  for (RestaurantEntity restaurantEntity : restaurantEntities) 
  {
    if (isRestaurantCloseByAndOpen(restaurantEntity, currentTime,
    latitude, longitude, servingRadiusInKms)) 
    {
      restaurants.add(modelMapper.map(restaurantEntity,Restaurant.class));
    }
  }
  return restaurants;
}





  private List<Restaurant> findAllRestaurantsCloseByFromCache(Double latitude, Double longitude, LocalTime currentTime, Double servingRadiusInKms)
  {
    List<Restaurant> restaurantList = new ArrayList<>();
    GeoLocation geoLocation = new GeoLocation(latitude, longitude);
    GeoHash geoHash = GeoHash.withCharacterPrecision(geoLocation.getLatitude(),geoLocation.getLongitude(), 7);

    try (Jedis jedis = redisConfiguration.getJedisPool().getResource())
    {
      String jsonStringFromCache = jedis.get(geoHash.toBase32());
      if (jsonStringFromCache == null) 
      {
        String createdJsonString = "";
        try 
        {
          restaurantList =
          findAllRestaurantsCloseFromDb(geoLocation.getLatitude(),
          geoLocation.getLongitude(),
          currentTime, servingRadiusInKms);
          createdJsonString = new
          ObjectMapper().writeValueAsString(restaurantList);
          } catch (JsonProcessingException e) {
          e.printStackTrace();
        }

      jedis.setex(geoHash.toBase32(),
      GlobalConstants.REDIS_ENTRY_EXPIRY_IN_SECONDS, createdJsonString);
      } 
      else 
      {
        try 
        {
          restaurantList = new
          ObjectMapper().readValue(jsonStringFromCache, new
          TypeReference<List<Restaurant>>(){});  
        } 
        catch (IOException e) 
        {
          e.printStackTrace();
        }
      }
    }

    return restaurantList;
  }
      




  // TODO: CRIO_TASK_MODULE_NOSQL
  // Objective:
  // 1. Check if a restaurant is nearby and open. If so, it is a candidate to be returned.
  // NOTE: How far exactly is "nearby"?




 

  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants whose names have an exact or partial match with the search query.
  @Override
  public List<Restaurant> findRestaurantsByName(Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {

    BasicQuery query = new BasicQuery("{name: {$regex: /" + searchString + "/i}}");
    List<RestaurantEntity> restaurants = mongoTemplate.find(query, RestaurantEntity.class, "restaurants");
    List<Restaurant> restaurantList = new ArrayList<Restaurant>();

    for(RestaurantEntity restaurant : restaurants)
    {
      if(isRestaurantCloseByAndOpen(restaurant, currentTime, latitude, longitude, servingRadiusInKms))
      {
        Restaurant res = new Restaurant();

        res.setRestaurant(
          restaurant.getId(),
          restaurant.getRestaurantId(),
          restaurant.getName(),
          restaurant.getCity(),
          restaurant.getImageUrl(),
          restaurant.getLatitude(),
          restaurant.getLongitude(),
          restaurant.getOpensAt(),
          restaurant.getClosesAt(),
          restaurant.getAttributes()      
        );

        restaurantList.add(res);
      }
    }
     return restaurantList;
  
  }


  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants whose attributes (cuisines) intersect with the search query.
  @Override
  public List<Restaurant> findRestaurantsByAttributes(
      Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {
      
      BasicQuery query = new BasicQuery("{attributes: {$regex: /" + searchString + "/i}}");  
      List<RestaurantEntity> restaurants = mongoTemplate.find(query, RestaurantEntity.class, "restaurants");
      List<Restaurant> restaurantList = new ArrayList<Restaurant>();

      for(RestaurantEntity restaurant : restaurants)
      {
        if(isRestaurantCloseByAndOpen(restaurant, currentTime, latitude, longitude, servingRadiusInKms))
        {
          Restaurant res = new Restaurant();
  
          res.setRestaurant(
            restaurant.getId(),
            restaurant.getRestaurantId(),
            restaurant.getName(),
            restaurant.getCity(),
            restaurant.getImageUrl(),
            restaurant.getLatitude(),
            restaurant.getLongitude(),
            restaurant.getOpensAt(),
            restaurant.getClosesAt(),
            restaurant.getAttributes()      
          );
  
          restaurantList.add(res);
        }
      }
       return restaurantList;
    
  }



  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants which serve food items whose names form a complete or partial match
  // with the search query.

  @Override
  public List<Restaurant> findRestaurantsByItemName(
      Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {

       
      BasicQuery query = new BasicQuery("{items.name: {$regex: /" + searchString + "/i}}");  
      List<RestaurantEntity> restaurants = mongoTemplate.find(query, RestaurantEntity.class, "restaurants");
      List<Restaurant> restaurantList = new ArrayList<Restaurant>();

      for(RestaurantEntity restaurant : restaurants)
      {
        if(isRestaurantCloseByAndOpen(restaurant, currentTime, latitude, longitude, servingRadiusInKms))
        {
          Restaurant res = new Restaurant();
  
          res.setRestaurant(
            restaurant.getId(),
            restaurant.getRestaurantId(),
            restaurant.getName(),
            restaurant.getCity(),
            restaurant.getImageUrl(),
            restaurant.getLatitude(),
            restaurant.getLongitude(),
            restaurant.getOpensAt(),
            restaurant.getClosesAt(),
            restaurant.getAttributes()      
          );
  
          restaurantList.add(res);
        }
      }
       return restaurantList;  

  }

  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants which serve food items whose attributes intersect with the search query.
  @Override
  public List<Restaurant> findRestaurantsByItemAttributes(Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {
    BasicQuery query = new BasicQuery("{items.attributes: {$regex: /" + searchString + "/i}}");  
      List<RestaurantEntity> restaurants = mongoTemplate.find(query, RestaurantEntity.class, "restaurants");
      List<Restaurant> restaurantList = new ArrayList<Restaurant>();

      for(RestaurantEntity restaurant : restaurants)
      {
        if(isRestaurantCloseByAndOpen(restaurant, currentTime, latitude, longitude, servingRadiusInKms))
        {
          Restaurant res = new Restaurant();
  
          res.setRestaurant(
            restaurant.getId(),
            restaurant.getRestaurantId(),
            restaurant.getName(),
            restaurant.getCity(),
            restaurant.getImageUrl(),
            restaurant.getLatitude(),
            restaurant.getLongitude(),
            restaurant.getOpensAt(),
            restaurant.getClosesAt(),
            restaurant.getAttributes()      
          );
  
          restaurantList.add(res);
        }
      }
       return restaurantList;
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

  @Async
  @Override
  public CompletableFuture<List<Restaurant>> findRestaurantsByNameMT(Double latitude, Double longitude, String searchString,
      LocalTime currentTime, Double servingRadiusInKms) {

      ModelMapper modelMapper = modelMapperProvider.get();

      List<RestaurantEntity> restaurantEntities = (List<RestaurantEntity>) restaurantRepository
        .findRestaurantsByNameExact(searchString).stream().collect(Collectors.toList()).get(0);

        List<Restaurant> restaurants = new ArrayList<Restaurant>();

      for (RestaurantEntity restaurantEntity : restaurantEntities) {
      if (isRestaurantCloseByAndOpen(restaurantEntity, currentTime, latitude, longitude, servingRadiusInKms)) {
        restaurants.add(modelMapper.map(restaurantEntity, Restaurant.class));
      }
    }

    return CompletableFuture.completedFuture(restaurants);
 }

 @Async
 @Override
 public CompletableFuture<List<Restaurant>> findRestaurantsByAttributesMT(Double latitude, Double longitude, String searchString,
     LocalTime currentTime, Double servingRadiusInKms) {

      ModelMapper modelMapper = modelMapperProvider.get();

      List<RestaurantEntity> restaurantEntities = (List<RestaurantEntity>) restaurantRepository
          .findRestaurantsByAttributesExact(searchString).stream().collect(Collectors.toList()).get(0);

      List<Restaurant> restaurants = new ArrayList<Restaurant>();

      for (RestaurantEntity restaurantEntity : restaurantEntities) {
        if (isRestaurantCloseByAndOpen(restaurantEntity, currentTime, latitude, longitude, servingRadiusInKms)) {
          restaurants.add(modelMapper.map(restaurantEntity, Restaurant.class));
        }
      }

   return CompletableFuture.completedFuture(restaurants);
 }


 @Async
 @Override
 public CompletableFuture<List<Restaurant>> findRestaurantsByItemNameMT(Double latitude, Double longitude, String searchString,
     LocalTime currentTime, Double servingRadiusInKms) {

      ModelMapper modelMapper = modelMapperProvider.get();
      List<ItemEntity> itemEntity = itemRepository.findRestaurantsByItemName(searchString).stream().collect(Collectors.toList());

      
      List<String> itemsStr = new ArrayList<String>();
      for (ItemEntity Ie : itemEntity) {
        itemsStr.add(Ie.getId());
      }

      List<MenuEntity> menuEntity = menuRepository.findMenusByItemsItemIdIn(itemsStr).stream().collect(Collectors.toList()).get(0);

                                                
        List<String> restIds = new ArrayList<String>();

        for (MenuEntity Me: menuEntity){
          restIds.add(Me.getRestaurantId());
      }


      List<RestaurantEntity> restaurantEntities =  restaurantRepository.findRestaurantsByid(restIds);


        List<Restaurant> restaurants = new ArrayList<Restaurant>();
        
        for (RestaurantEntity restaurantEntity : restaurantEntities) {
          if (isRestaurantCloseByAndOpen(restaurantEntity, currentTime, latitude, longitude, servingRadiusInKms)) {
            restaurants.add(modelMapper.map(restaurantEntity, Restaurant.class));
          }
        }
        
  
  
        return CompletableFuture.completedFuture(restaurants);
     }



  @Async
  @Override
  public CompletableFuture<List<Restaurant>> findRestaurantsByItemAttributesMT(Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {

      ModelMapper modelMapper = modelMapperProvider.get();
      List<ItemEntity> listItems =  itemRepository.findRestaurantsByItemAtt(searchString);


      List<String> itemsStr = new ArrayList<String>();
      for (ItemEntity Ie: listItems){
          itemsStr.add(Ie.getId());
      }
    
      List<MenuEntity> menuEntity = menuRepository.findMenusByItemsItemIdIn(
                                      itemsStr).stream().collect(Collectors.toList()).get(0);

      List<String> restIds = new ArrayList<String>();

      for (MenuEntity Me: menuEntity){
        restIds.add(Me.getRestaurantId());
      }

    List<RestaurantEntity> restaurantEntities =  restaurantRepository.findRestaurantsByid(restIds);

      List<Restaurant> restaurants = new ArrayList<Restaurant>();
      
      for (RestaurantEntity restaurantEntity : restaurantEntities) {
        if (isRestaurantCloseByAndOpen(restaurantEntity, currentTime, latitude, longitude, servingRadiusInKms)) {
          restaurants.add(modelMapper.map(restaurantEntity, Restaurant.class));
        }
      }

      return CompletableFuture.completedFuture(restaurants);

  }
        
    
}





