
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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
// import javax.smartcardio.ATR;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class RestaurantServiceImpl implements RestaurantService {

  private final Double peakHoursServingRadiusInKms = 3.0;
  private final Double normalHoursServingRadiusInKms = 5.0;
  
  @Autowired
  private RestaurantRepositoryService restaurantRepositoryService;


  // TODO: CRIO_TASK_MODULE_RESTAURANTSAPI - Implement findAllRestaurantsCloseby.
  // Check RestaurantService.java file for the interface contract.
  @Override
  public GetRestaurantsResponse findAllRestaurantsCloseBy(
      GetRestaurantsRequest getRestaurantsRequest, LocalTime currentTime) {

      double currentLatitude = getRestaurantsRequest.getLatitude();
      double currentLongitude = getRestaurantsRequest.getLongitude();

      Double serviceRange = peakHoursServingRadiusInKms;

      if(!isPeakHours(currentTime))
      {
        serviceRange = normalHoursServingRadiusInKms;
      }
      
      List<Restaurant> restaurantsList;
      restaurantsList = restaurantRepositoryService.findAllRestaurantsCloseBy(currentLatitude, currentLongitude, currentTime, serviceRange);
      
      
     return new GetRestaurantsResponse(restaurantsList);

  }

  private boolean isPeakHours(LocalTime currentTime)
  {
    LocalTime start1 = LocalTime.of(8,0);
    LocalTime end1 = LocalTime.of(10,0);

    LocalTime start2 = LocalTime.of(13,0);
    LocalTime end2 = LocalTime.of(14,0);

    LocalTime start3 = LocalTime.of(19,0);
    LocalTime end3 = LocalTime.of(21,0);

    return  (currentTime.isAfter(start1) && currentTime.isBefore(end1)) ||
            (currentTime.isAfter(start2) && currentTime.isBefore(end2)) ||
            (currentTime.isAfter(start3) && currentTime.isBefore(end3)) ||
            (currentTime.equals(start1) || currentTime.equals(end1)) ||
            (currentTime.equals(start2) || currentTime.equals(end2)) ||
            (currentTime.equals(start3) || currentTime.equals(end3)); 
          
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
  
      Double serviceRange = peakHoursServingRadiusInKms;

      if(!isPeakHours(currentTime))
      {
        serviceRange = normalHoursServingRadiusInKms;
      }

      List<List<Restaurant>> listOfRestaurant = new ArrayList<>();
      String searchString = getRestaurantsRequest.getSearchFor();

      if(!searchString.isEmpty())
      {
        listOfRestaurant.add(restaurantRepositoryService.findRestaurantsByName(getRestaurantsRequest.getLatitude(),
        getRestaurantsRequest.getLongitude(), searchString, currentTime, serviceRange));

        listOfRestaurant.add(restaurantRepositoryService.findRestaurantsByAttributes(getRestaurantsRequest.getLatitude(),
        getRestaurantsRequest.getLongitude(), searchString, currentTime, serviceRange));

        listOfRestaurant.add(restaurantRepositoryService.findRestaurantsByItemName(getRestaurantsRequest.getLatitude(),
        getRestaurantsRequest.getLongitude(), searchString, currentTime, serviceRange));

        listOfRestaurant.add(restaurantRepositoryService.findRestaurantsByItemAttributes(getRestaurantsRequest.getLatitude(),
        getRestaurantsRequest.getLongitude(), searchString, currentTime, serviceRange));


        Set<String> restaurantSet = new HashSet<>();
        List<Restaurant> resList = new ArrayList<>();
        
        for(List<Restaurant> resL  : listOfRestaurant)
        {
          for(Restaurant r : resL)
          {
            if(!restaurantSet.contains(r.getRestaurantId()))
            {
              resList.add(r);
              restaurantSet.add(r.getRestaurantId());
            }
          }
        }

        return new GetRestaurantsResponse(resList);
      }

      else{
        return new GetRestaurantsResponse(new ArrayList<>());
      }

    }



 
  // TODO: CRIO_TASK_MODULE_MULTITHREADING
  // Implement multi-threaded version of RestaurantSearch.
  // Implement variant of findRestaurantsBySearchQuery which is at least 1.5x time faster than
  // findRestaurantsBySearchQuery.
  @Override
  public GetRestaurantsResponse findRestaurantsBySearchQueryMt(
      GetRestaurantsRequest getRestaurantsRequest, LocalTime currentTime) {

      LinkedHashSet<Restaurant> resHash = new LinkedHashSet<>();
      String searchString = getRestaurantsRequest.getSearchFor();
      List<Restaurant> restaurants = new ArrayList<>();

      if(searchString != "")
      {
        Double latitude = getRestaurantsRequest.getLatitude();
        Double longitude = getRestaurantsRequest.getLongitude();
        Double servingRadiusInKms = isPeakHours(currentTime) ? peakHoursServingRadiusInKms : normalHoursServingRadiusInKms;

        CompletableFuture<List<Restaurant>> list1 = restaurantRepositoryService.findRestaurantsByNameMT(latitude, longitude,
        searchString, currentTime, servingRadiusInKms);

        CompletableFuture<List<Restaurant>> list2 = restaurantRepositoryService.findRestaurantsByAttributesMT(latitude,
        longitude, searchString, currentTime, servingRadiusInKms);

        CompletableFuture<List<Restaurant>> list3 = restaurantRepositoryService.findRestaurantsByItemNameMT(latitude,
        longitude, searchString, currentTime, servingRadiusInKms);

        CompletableFuture<List<Restaurant>> list4 = restaurantRepositoryService.findRestaurantsByItemAttributesMT(latitude,
        longitude, searchString, currentTime, servingRadiusInKms);

        CompletableFuture.allOf(list1, list2, list3, list4).join();

        resHash.addAll((Collection<? extends Restaurant>) list1);
        resHash.addAll((Collection<? extends Restaurant>) list2);
        resHash.addAll((Collection<? extends Restaurant>) list3);
        resHash.addAll((Collection<? extends Restaurant>) list4);

        restaurants = new ArrayList<Restaurant>(resHash);

      }

      return new GetRestaurantsResponse(restaurants);
  }
  
}




