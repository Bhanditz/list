<?php

/* The List powered by Creative Commons

   Copyright (C) 2014, 2015 Creative Commons Corporation

   based on:

   GNU FM -- a free network service for sharing your music listening habits
   GNU Archie Framework -- a web application framework derived from GNU FM

   Copyright (C) 2009, 2015 Free Software Foundation, Inc

   This program is free software: you can redistribute it and/or modify
   it under the terms of either the GNU Affero General Public License or
   the GNU General Public License as published by the
   Free Software Foundation, either version 3 of the Licenses, or
   (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU Affero General Public License for more details.

   You should have received a copy of the GNU General Public License and
   the GNU Affero General Public License along with this program.

   If not, see <http://www.gnu.org/licenses/>.

*/

require_once(dirname(__DIR__).'/database.php');
require(dirname(__DIR__).'/data/User.php');
require(dirname(__DIR__).'/data/List.php');
require(dirname(__DIR__).'/version.php');
require(__DIR__.'/klein.php');
require(__DIR__.'/functions.php');
require(__DIR__.'/UserLogin.php');

header('Content-Type: text/javascript; charset=utf8');
header('Access-Control-Allow-Origin: '.$base_url.'/');
header('Access-Control-Max-Age: 3628800');
header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE');

                        
with('/api/version', function () {

    respond('GET', '/?', function ($request, $response) {
        global $api_version, $app_version, $webapp_version;
        $version = ['api_version'=>$api_version,
                    'app_version'=>$app_version,
                    'webapp_version'=>$webapp_version];
        $output = json_encode($version, JSON_PRETTY_PRINT);
        echo $output;
    });
});

with('/api/category', function () {

    respond('GET', '/?', function ($request, $response) {

        $list = new UserList();

        $selection = $list->getCategories(20);
        
        $output = json_encode($selection, JSON_PRETTY_PRINT);
        echo $output;


    });

    respond('GET', '/[:id]', function ($request, $response) {
        // Show items from a single category

    $id =$request->id;

        $list = new UserList();

        $selection = $list->getCategoriesList(20, $id);

        $output = json_encode($selection, JSON_PRETTY_PRINT);
        echo $output;

    });

});


with('/api/items', function () {

    respond('GET', '/?', function ($request, $response) {

        $list = new UserList();

        $selection = $list->getNewList(20);
        
        $output = json_encode($selection, JSON_PRETTY_PRINT);
        echo $output;


    });

    respond('GET', '/[:id]', function ($request, $response) {
        // Show items from a single category

    $id =$request->id;

        $list = new UserList();

        $selection = $list->getSingleListItem($id);

        $output = json_encode($selection, JSON_PRETTY_PRINT);
        echo $output;

    });

    respond('POST','/[:id]', function ($request, $response) {

        echo "This is where you can add things to your list";

    });

});

function validateUserSession ($request) {
    $sessionuserid = $request->param('userid', false);
    $skey = $request->param('skey', false);
    if ($sessionuserid != false) {
        if(User::sessionIsValid ($sessionuserid, $skey)) {
            $sessionuserid = intval($sessionuserid);
        } else {
            $sessionuserid = false;
            $skey = false;
        }
    }
    return [$sessionuserid, $skey];
}

function formatPhotoListResults ($photos) {
  $result = ['photos'=>$photos,
             'count'=>count($photos)];
  if ($result['count'] > 0) {
    //FIXME: If we return the last item, this will be wrong
    $result['next_id'] = 1 + array_reduce($photos,
                                          function($carry, $item) {
                                            return
                                              $item['itemid'] > $carry ?
                                              $item['itemid'] : $carry;
                                          }, $photos[0]['itemid']);
      $result['from_id'] = array_reduce($photos,
                                        function($carry, $item) {
                                          return
                                            $item['itemid'] < $carry ?
                                            $item['itemid'] : $carry;
                                        }, $photos[0]['itemid']);
  }
  return $result;
}

// Note photos, not photo

with('/api/photos', function () {

    // Get the global photo list
    respond('GET', '/?', function ($request, $response) {
        list($sessionuserid, $skey) = validateUserSession($request);
        $count = abs(intval($request->param('count', 20)));
        $offset = abs(intval($request->param('from', 1)));
        $list = new UserList();
        $photos = $list->getPhotosList($sessionuserid, $count, $offset);
        $result = formatPhotoListResults ($photos);
        $output = json_encode($result, JSON_PRETTY_PRINT);
        echo urldecode($output);
      });

    respond('GET', '/[:user]', function ($request, $response) {
        $listuserid = $request->user;
        $count = abs(intval($request->param('count', 20)));
        $offset = abs(intval($request->param('from', 1)));
        list($sessionuserid, $skey) = validateUserSession($request);
        $photos = UserList::getPhotosList($sessionuserid, $count, $offset,
                                          $listuserid);
        $result = formatPhotoListResults ($photos);
        $output = json_encode($result, JSON_PRETTY_PRINT);

        echo $output;

    });

    respond('POST', '/[:userid]/[:id]', function ($request, $response) {

        $userid = $request->userid;
        $listitem = $request->id;

        $filedata = $request->param('filedata');

        $filedata = base64_decode($filedata);

        if (strlen($filedata) > 50) {

        $tmp = "" . tempnam("../list-uploads/", "process-me-" . $userid . "-");

        $image = fopen($tmp, "w") or die("Unable to open file!");
        fwrite($image, $filedata);
        fclose($image);

        
        $list = new UserList();       
        $filename = $tmp;
                    
        $result = $list->addPhoto($userid, $filename, $listitem);

        } else {

            http_response_code(400);
        }

    });

});


// Note photo, not photos

with('/api/photo', function () {

    // Get the global photo list
    respond('POST', '/[:photoid]/like', function ($request, $response) {
        list($sessionuserid, $skey) = validateUserSession($request);
        if ($sessionuserid) {
            $photoid = $request->photoid;
            list($result_code, $result) = User::likePhoto($sessionuserid,
                                                          $photoid);
            http_response_code($result_code);
        } else {
            $result = ['success' => false];
            http_response_code(401);
        }
        echo json_encode($result, JSON_PRETTY_PRINT);
    });

});

with('/api/block', function () {

    respond('POST', '/photo/[:photoid]', function ($request, $response) {
        $result_code = 200;

        try {
          $skey = $request->param('skey');
          $userid = $request->param('userid');
          $photoid = $request->photoid;
        } catch (Exception $e) {
          $result_code = 400;
        }

        if ($result_code == 200) {
          // This will check that the session is valid
          $result_code = User::blockPhoto($userid, $skey, $photoid);
        }

        http_response_code($result_code);

    });

});

with('/api/users', function () {
    respond('POST', '/login', function ($request, $response) {
        $login = new UserLogin();
        $login->username = urlencode($request->param('username'));
        $login->password = urlencode($request->param('password'));
        $login->GUIDToMerge = $request->param('guid', false);
        $session = $login->login();
        if($session) {
          $output = json_encode($session, JSON_PRETTY_PRINT);
          echo urldecode($output);
        } else {
          http_response_code(401);
        }
    });

    respond('POST', '/register', function ($request, $response) {

        echo "This is the register stub";

    });

    respond('GET', '/[:email]', function ($request, $response) {
        echo "This is the GET USER stub";

    });
});

with('/api/suggestions', function () {

    respond('POST', '/[:userid]', function ($request, $response) {

        $userid = $request->userid;
        $categoryid = $request->param('categoryid');
        $description = $request->param('description');
        $title = $request->param('title');
        
        $filedata = $request->param('filedata');

        if ($filedata) {
            $filedata = base64_decode($filedata);
        }

        if (strlen($filedata) > 50) {

        $tmp = "" . tempnam("../list-uploads/", "suggest-me-" . $userid . "-");

        $image = fopen($tmp, "w") or die("Unable to open file!");
        fwrite($image, $filedata);
        fclose($image);
        
        $filename = $tmp;

        }

        $list = new UserList;       
        
        $result = $list->addUserSuggestion($userid, $filename, $categoryid, $description, $title);
        
    });

});

with('/api/makers', function () {

    respond('GET', '/[:id]', function ($request, $response) {

    $id =$request->id;

        $list = new UserList();

        $selection = $list->getMakerProfile($id);

        $output = json_encode($selection, JSON_PRETTY_PRINT);
        echo $output;

    });

});

with('/api/userlist/delete', function() {

    respond('POST', '/[:userid]/[:id]', function ($request, $response) {

        $userid = $request->userid;
        $listitem = $request->id;      
        $skey = $request->param('skey');

        $list = new UserList();

        $response = $list->deleteItem($userid, $listitem,$skey);

        $output = json_encode($response, JSON_PRETTY_PRINT);
        echo $output;
    });

});

with('/api/userlist', function () {

    respond('GET', '/[:id]', function ($request, $response) {

    $id=$request->id;

        $list = new UserList();

        $selection = $list->getUserTopList(100, $id);

        $output = json_encode($selection, JSON_PRETTY_PRINT);

        echo $output;

    });

    respond('POST','/[:user]/[:id]', function ($request, $response) {

        $item=$request->id;
    $userid=$request->user;

        // We'll need to figure out a way to do these more securely

        $list = new UserList();
        $save = $list->addToMyList($item, $userid);

        echo "[]";

    });

});

with('/api/usercategories/add', function() {

    respond('POST', '/[:user]/[:id]', function ($request, $response) {

        $categoryid=$request->id;
    $userid=$request->user;

        $list = new UserList();
        $save = $list->addUserCategory($categoryid, $userid);

    });

});

with('/api/usercategories/delete', function() {

    respond('POST', '/[:user]/[:id]', function ($request, $response) {

        $categoryid=$request->id;
    $userid=$request->user;

        $list = new UserList();
        $save = $list->deleteUserCategory($categoryid, $userid);

    });

});

with('/api/usercategories/list', function() {

    respond('GET', '/[:user]', function ($request, $response) {

    $userid=$request->user;

        $list = new UserList();
        $save = $list->getUserCategories($userid);

        $output = json_encode($save, JSON_PRETTY_PRINT);

        echo $output;

    });

});

with('/api', function () {

    respond('GET', '/', function ($request, $response) {

        header('Content-Type: text/html; charset=utf8');
        require_once('api.html');

    });

});


dispatch();
