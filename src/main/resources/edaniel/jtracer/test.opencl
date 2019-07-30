#define PI  3.14159265359f

#define SIZEX (1280)
#define SIZEY (720)

#define DEFAULT_COLOR (float3)(0.1,0.1,0.1)

#define GET_Y(id) (id/SIZEX)
#define DEBUG(id) (GET_Y(id) == SIZEY/2 && id-GET_Y(id)*SIZEX == SIZEX/2)
//#define DEBUG(id) 0
#define PRINT_VECTOR(name,vector) (printf("%s: %f %f %f", name, vector.x, vector.y, vector.z))

//type
#define SPHERE 0
#define MESH 1

typedef struct s_AABB {
    float3      bounds[2];
}               AABB;

typedef struct s_KDNode {
    AABB        bbox;
    int         left;
    int         right;
    int         split_axis;
    float       split_pos;
    int         object_index;
    int         object_count;
}               KDNode;

typedef struct s_KDTree {
    int total_byte_size;
    int node_count;
    char data;
}               KDTree;

typedef struct __attribute__((packed)) s_Test {
    int         type;
    float3      position;
}               Test;

typedef struct __attribute__((packed, aligned(64))) s_Object {
	int         type;
	float3 		position;
	float3      color;
//	float 		size;
//	Material	mat;
//	float4 		rotation;
//	int 		type;
}				Object;

typedef struct __attribute__((packed)) s_Sphere {
    int         type;
    float3      position;
    float3      color;
    float       size;
}               Sphere;

typedef struct __attribute__((packed, aligned(64))) s_Mesh {
	int         type;
	float3 		position;
    float3      color;
	int         nb_triangles;
	int         nb_normals;
	char        data;
//	float 		size;
//	Material	mat;
//	float4 		rotation;
//	int 		type;
}				Mesh;

typedef struct s_Ray {
    union {
        float3  origin;
        float   origin_[4];
    };
    union {
	    float3 	direction;
        float   direction_[4];
    };
	float3 	position;
    float 	distance;
	int 	id_object;
    float3  normal;
	float3 	color;
	char3   sign;
	union {
	    float3  invdir;
	    float   invdir_[4];
	};
}				Ray;

void vectorRotation(float3* vector, const float3 angles)
{
    float3 __r = (*vector);
    /* X axis */
    __r.y = (*vector).y * half_cos(angles.x) - (*vector).z * half_sin(angles.x);
    __r.z = (*vector).y * half_sin(angles.x) + (*vector).z * half_cos(angles.x);
    (*vector) = __r;
    __r = (*vector);
    /* Y axis */
    __r.z = (*vector).z * half_cos(angles.y) - (*vector).x * half_sin(angles.y);
    __r.x = (*vector).z * half_sin(angles.y) + (*vector).x * half_cos(angles.y);
    (*vector) = __r;
}

float ray_triangle(const Ray *r, __global float3 *data, float uv[])
{
	float3 tvec = r->origin - data[0];
	float3 pvec = cross(r->direction, data[2]);
	float  det = dot(data[1], pvec);

	det = 1.f / det;

	float u = dot(tvec, pvec) * det;

	if (u < 0.0f || u > 1.0f)
		return -1.0f;

	float3 qvec = cross(tvec, data[1]);

	float v = dot(r->direction, qvec) * det;

	if (v < 0.0f || (u + v) > 1.0f)
		return -1.0f;

    uv[0] = u;
    uv[1] = v;
	return dot(data[2], qvec) * det;
}

float ray_sphere(Ray *ray, float size)
{
	float a = dot(ray->direction, ray->direction);
	float b = 2.f * dot(ray->direction, ray->origin);
	float c = dot(ray->origin, ray->origin) - size * size;
	float delta = b*b - 4.f * a * c;

	float k = (-b - sqrt(delta)) / (2.f * a);
	float kk = (-b + sqrt(delta)) / (2.f * a);

	if (k < 0.f && kk < 0.f)
		return -1;

	k = min(k, kk);

	if (k > 0.f)
		return k;
	else if (kk > 0.f)
		return kk;
	return -1;
}

int ray_aabb_OLD(Ray *r, __global AABB *aabb) {
    float tmin, tmax, tymin, tymax, tzmin, tzmax;

    tmin = (aabb->bounds[r->sign.x].x - r->origin.x) * r->invdir.x;
    tmax = (aabb->bounds[1-r->sign.x].x - r->origin.x) * r->invdir.x;
    tymin = (aabb->bounds[r->sign.y].y - r->origin.y) * r->invdir.y;
    tymax = (aabb->bounds[1-r->sign.y].y - r->origin.y) * r->invdir.y;

    if ((tmin > tymax) || (tymin > tmax))
        return false;
    if (tymin > tmin)
        tmin = tymin;
    if (tymax < tmax)
        tmax = tymax;

    tzmin = (aabb->bounds[r->sign.z].z - r->origin.z) * r->invdir.z;
    tzmax = (aabb->bounds[1-r->sign.z].z - r->origin.z) * r->invdir.z;

    if ((tmin > tzmax) || (tzmin > tmax))
        return false;
    if (tzmin > tmin)
        tmin = tzmin;
    if (tzmax < tmax)
        tmax = tzmax;

    return true;
}

int ray_aabb(Ray *ray, __global AABB *aabb, float t[]) {
    t[0] = 0;
    t[1] = MAXFLOAT;

    float t0, t1;
    float tnear, tfar;
    float tmp;

    t0 = (aabb->bounds[0].x - ray->origin.x) * ray->invdir.x;
    t1 =  (aabb->bounds[1].x - ray->origin.x) * ray->invdir.x;
    tnear = select(t0, t1, t0 > t1);
    tfar = select(t0, t1, t0 < t1);
    t[0] = select(t[0], tnear, tnear > t[0]);
    t[1] = select(t[1], tfar, tfar < t[1]);
    //if (t[0] > t[1])
     //   return false;

    t0 = (aabb->bounds[0].y - ray->origin.y) * ray->invdir.y;
    t1 =  (aabb->bounds[1].y - ray->origin.y) * ray->invdir.y;
    tnear = select(t0, t1, t0 > t1);
    tfar = select(t0, t1, t0 < t1);
    t[0] = select(t[0], tnear, tnear > t[0]);
    t[1] = select(t[1], tfar, tfar < t[1]);
    //if (t[0] > t[1])
      //  return false;

    t0 = (aabb->bounds[0].z - ray->origin.z) * ray->invdir.z;
    t1 =  (aabb->bounds[1].z - ray->origin.z) * ray->invdir.z;
    tnear = select(t0, t1, t0 > t1);
    tfar = select(t0, t1, t0 < t1);
    t[0] = select(t[0], tnear, tnear > t[0]);
    t[1] = select(t[1], tfar, tfar < t[1]);
    if (t[0] > t[1])
        return false;
    return true;
}

typedef struct s_ToDoKD {
    __global KDNode     *node;
    float               tmin, tmax;
}               ToDoKD;

int smart_kdtree(Ray *ray, __global KDNode *tree, __global int *indices, __global float3 *triangles, __global float3 *normals, __global KDNode *node) {
    float min_max[2];

   // if (DEBUG(get_global_id(0)))
     //       printf("%d %f %f %f", ray_aabb_OLD(ray, &tree->bbox), tree[0].bbox.bounds[0].x,tree[0].bbox.bounds[0].y,tree[0].bbox.bounds[0].z);

    if (ray_aabb(ray, &tree->bbox, min_max) == false)
        return false;

    char has_hit = false;

    ToDoKD  todo[64];
    int     todo_pos = 0;

    while (todo_pos != -1) {
        if (ray->distance < min_max[0] && ray->distance > 0.f) // no more closer intersection
            break;
        if (node->object_count == 0) { // process leaf
            int axis = node->split_axis;
            float t_split = (node->split_pos - ray->origin_[axis]) * ray->invdir_[axis];
            char left_first = ray->origin_[axis] < node->split_pos ||
                                (ray->origin_[axis] == node->split_pos && ray->direction_[axis] < 0.f);

            __global KDNode *first, *second;
            if (left_first) {
                first = &tree[node->left];
                second = &tree[node->right];
            }
            else {
                first = &tree[node->right];
                second = &tree[node->left];
            }

            if (t_split > min_max[1] || t_split <= 0)
                node = first;
            else if (t_split < min_max[0])
                node = second;
            else {
                todo[todo_pos].node = second;
                todo[todo_pos].tmin = t_split;
                todo[todo_pos].tmax = min_max[1];
                todo_pos++;
                node = first;
                min_max[1] = t_split;
            }
        }
        else { // process triangles
            for (int i = 0 ; i < node->object_count ; i++) {
                int index = indices[i + node->object_index] * 3;
                __global float3 *object = &triangles[index];

                float uv[2];
                float tmp_k = ray_triangle(ray, object, uv);
                if (tmp_k > 0.f && (ray->distance < 0.f || tmp_k < ray->distance)) {
                    ray->distance = tmp_k;
                    ray->id_object = index;
                    has_hit = 1;
                    ray->normal = (normals[index+1] * uv[0] + normals[index+2] * uv[1] + normals[index] * (1.f - (uv[0] + uv[1])));
                    ray->normal = normalize(ray->normal);
                }
            }

            if (todo_pos > 0) {
                todo_pos--;
                node = todo[todo_pos].node;
                min_max[0] = todo[todo_pos].tmin;
                min_max[1] = todo[todo_pos].tmax;
            }
            else
                break;
        }
    }

    return has_hit;
}

// 295ms for 2000s and 263 nodes
int ray_kdtree(Ray *ray, __global KDNode *tree, __global int *indices, __global float3 *triangles, __global float3 *normals, __global KDNode *node) {

    if (DEBUG(get_global_id(0)) && tree == node) {
//        node = &tree[node->left];
//        printf("%d", sizeof(KDNode));
//        printf("min %f %f %f", node[0].bbox.bounds[0].x,node[0].bbox.bounds[0].y,node[0].bbox.bounds[0].z);
//        printf("max %f %f %f", node[0].bbox.bounds[1].x,node[0].bbox.bounds[1].y,node[0].bbox.bounds[1].z);
//        printf("%d %d", node->left, node->right);
//        printf("%d %f", node->split_axis, node->split_pos);
//        printf("%d %d", node->object_index, node->object_count);
    }

    if (node->object_count == 0) {
        char success = 0;
        float minmax[2];
        if (tree->left != 0) {
            if (ray_aabb_OLD(ray, &tree[node->left].bbox))
                success |= ray_kdtree(ray, tree, indices, triangles, normals, &tree[node->left]);
        }
        if (tree->right != 0) {
            if (ray_aabb_OLD(ray, &tree[node->right].bbox))
                success |= ray_kdtree(ray, tree, indices, triangles, normals, &tree[node->right]);
        }
        return success;
    }
    else {
        char has_hit = 0;
        for (int i = 0 ; i < node->object_count ; i++) {
            int index = indices[i + node->object_index] * 3;
//            if (DEBUG(get_global_id(0)))
//                printf("%d", indices[0]);
            __global float3 *object = &triangles[index];

            float uv[2];
            float tmp_k = ray_triangle(ray, object, uv);
            //if(tmp_k>0.f)
                //printf("%f",tmp_k);
            if (tmp_k > 0.f && (ray->distance < 0.f || tmp_k < ray->distance)) {
                ray->distance = tmp_k;
                ray->id_object = index;
                has_hit = 1;
                ray->normal = (normals[index+1] * uv[0] + normals[index+2] * uv[1] + normals[index] * (1.f - (uv[0] + uv[1])));
                ray->normal = normalize(ray->normal);
            }
        }

        return has_hit;

    }

    return 0;

}

float ray_mesh(Ray *ray, __global Mesh *mesh) {
    __global float3 *triangle = &mesh->data;
    __global float3 *normal = &triangle[mesh->nb_triangles * 3];
    __global KDTree *kdtree = &normal[mesh->nb_normals];
    __global KDNode *nodes = &kdtree->data;
    __global int    *indexes = &nodes[kdtree->node_count];

    //if (DEBUG(get_global_id(0)))
      //  printf("nb_normal=%d tree totalsze=%d offset=%d", mesh->nb_normals, kdtree->total_byte_size, kdtree->node_count);

//    float minmax[2];
//    if (ray_aabb_OLD(ray, &nodes->bbox))
//        ray_kdtree(ray, nodes, indexes, triangle, normal, nodes);
        smart_kdtree(ray, nodes, indexes, triangle, normal, nodes);
    //ray->distance = ray_triangle(ray, &triangle[indexes[0]], uv);
    return ray->distance;
    //for (int i = 0 ; i < mesh->nb_triangles ; i++) {
        //PRINT_VECTOR("test", (*triangle));
        //float uv[2];
        //float k = ray_triangle(ray, triangle, uv);
        //if (k > 0.f && (ray->distance < 0.f || k < ray->distance)) {
           // ray->distance = k;
 //           ray->normal = normal[0];
           // ray->normal = (normal[1] * uv[0] + normal[2] * uv[1] + normal[0] * (1.f - (uv[0] + uv[1]))) / 3;
          //  ray->normal = normalize(ray->normal);
      //  }

    //    triangle = &triangle[3];
      //  normal = &normal[3];
   // }

   // return -1;
}

float3 getDirection(int2 xy, float3 rotation) {
	float fov = 36;
	float image_scale = tan(PI/180.f*(fov * 0.5));
	float image_ratio = (float)SIZEX / SIZEY;

    float3 dir;
    dir.x = (2 * (xy.x + 0.5f) / SIZEX - 1) * image_scale * image_ratio;
    dir.y = -(1 - 2 * (xy.y + 0.5) / SIZEY) * image_scale;
    dir.z = 1;
    dir = normalize(dir);
    vectorRotation(&dir, rotation);
    return dir;
}

void lighting(Ray *ray, __global Object *object) {
    float3 light_pos = (float3)(100,100,-1000);
    //float3 light_pos = light->position;
    float3 light_vec = normalize(light_pos - ray->position);
    float3 normal = ray->normal;

//    if (dot(normal, light_vec) > 0)
  //      normal = -normal;

    float angle = dot(light_vec, normal);
    if (angle < 0)
        angle = 0;
    if (angle > 1)
        angle = 1;
    ray->color = ((float3)(1)) * angle;

}

__kernel
void test(__write_only image2d_t image, long time, __global float *origin, __global float *rotation, __global int *objects, __global int *objects_index,
                    int nb_objects) {
    int id = get_global_id(0);

    int y = id / SIZEX;
    int x = id - (y * SIZEX);
    int2 xy = (int2)(x,y);

    Ray ray;
    ray.distance = -1;
    ray.direction = getDirection(xy, *((__global float3*)rotation));
    ray.origin = (float3)(origin[0],origin[1],origin[2]);
    ray.color = (float3)(1,1,1);
    ray.invdir = 1.f / ray.direction;
    ray.sign.x = (ray.invdir.x < 0);
    ray.sign.y = (ray.invdir.y < 0);
    ray.sign.z = (ray.invdir.z < 0);

    for (int i = 0 ; i < nb_objects ; i++) {
        if (objects_index[i] != 0 && DEBUG(id))
            printf("%d %d", i, objects_index[i]);
        __global Object *object = &objects[objects_index[i]];
        //ray.origin -= object->position;
        float k;
        if (object->type == SPHERE)
            k = ray_sphere(&ray, ((__global Sphere *)object)->size);
        else if (object->type == MESH)
            k = ray_mesh(&ray, object);
        //printf("type=%d\n", object->type);
        //printf("x=%f y=%f z=%f w=%f", object->position.x, object->position.y, object->position.z);
        //PRINT_VECTOR("t", object->position);
        //ray.origin += object->position;
        if (k > 0 && (ray.distance < 0 || k < ray.distance)) {
            ray.id_object = objects_index[i];
            ray.distance = k;
        }
    }

//ray.distance = ray_aabb(&ray, &kdtree[kdtree[kdtree[0].left].left].bbox);
//if (ray.distance) {
//ray.distance = -1;
//ray_kdtree(&ray, kdtree, spheres_indices, objects, &kdtree[kdtree[kdtree[0].left].left]);
//}
    if (ray.distance > 0) {
        ray.color = (float3)(1,0,0);
        ray.position = ray.origin + ray.direction * ray.distance;
        lighting(&ray, &objects[ray.id_object]);
    }

    //printf("%f", origin[1]);

    write_imagef(image, xy, (float4)(ray.color,1));

}